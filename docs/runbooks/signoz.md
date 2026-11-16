# Runbook: dựng / vận hành SigNoz trên `unbutu` (ADR-0006)

Project `observability` **không** đi qua CI/CD: admin chạy tay trên server. Không cần secret nào từ AWS.
File nguồn: `deploy/compose.observability.yml` + `deploy/signoz/`. Thư mục trên server: `/opt/unbutu-platform/observability`.

## 0. Điều kiện
- RAM trống ≥ 4 GiB (`free -h`). Stack dùng tối đa khoảng 3.8 GiB (ClickHouse 2g, SigNoz 512m, collector 512m, keeper 256m, 2 job một lần).
- PR chứa compose đã merge vào `main`. Lấy file **đúng commit trên `main`**, không lấy từ nhánh.

## 1. Chép cấu hình lên server (từ Mac, trong repo đã `git pull` ở `main`)
```bash
SHA=$(git rev-parse --short HEAD)
tar czf - deploy/compose.observability.yml deploy/signoz \
  | ssh unbutu "sudo mkdir -p /opt/unbutu-platform/observability && sudo tar xzf - -C /opt/unbutu-platform/observability --strip-components=1 && echo $SHA | sudo tee /opt/unbutu-platform/observability/REVISION"
```
Kết quả: `/opt/unbutu-platform/observability/{compose.observability.yml,signoz/…,REVISION}`.

## 2. Khởi động
```bash
ssh unbutu 'cd /opt/unbutu-platform/observability && sudo docker compose -f compose.observability.yml up -d --wait'
ssh unbutu 'sudo docker compose -p observability ps -a --format "{{.Service}} {{.Status}}"'
```
Kỳ vọng: `user-scripts` và `migrator` ở trạng thái **Exited (0)**. Keeper, ClickHouse và `signoz-signoz-0` **healthy**. `ingester` **Up**.
Lần đầu migrator mất 1–3 phút. Nếu lỗi: `sudo docker logs signoz-telemetrystore-migrator`.

## 3. Mở UI trong tailnet (đã chốt phương án A, ADR-0006)
```bash
ssh unbutu 'curl -fsS http://127.0.0.1:3301/api/v1/health && echo'
ssh unbutu 'sudo tailscale serve --bg --https=8443 http://127.0.0.1:3301 && tailscale serve status'
```
Mở `https://unbutu.tail3c11f4.ts.net:8443` từ máy trong tailnet. `serve status` phải vẫn còn `/ → 127.0.0.1:8080 (Funnel on)` cho cổng 443,
còn cổng 8443 **không** có Funnel. Kiểm tra từ ngoài tailnet: `curl --resolve unbutu.tail3c11f4.ts.net:8443:$(dig +short @1.1.1.1 unbutu.tail3c11f4.ts.net | head -1) https://unbutu.tail3c11f4.ts.net:8443` phải **thất bại**.

## 4. Thiết lập ban đầu (làm ngay sau bước 3)
1. **Đăng ký tài khoản admin ngay**: người đăng ký đầu tiên thành Admin, nên đừng để máy khác trong tailnet đăng ký trước.
2. Settings → General → Retention: log, trace và metrics đều **7 ngày**.
3. (Làm sau, theo §7) Tạo user `developer` với role Viewer.

## 5. Kiểm chứng log từ prod
Label log trong `compose.app.yml` chỉ có hiệu lực **sau lần deploy prod kế tiếp** (container được tạo lại).
Sau khi deploy:
```bash
curl -s https://unbutu.tail3c11f4.ts.net/api/catalog/ping
```
Vào SigNoz → Logs, lọc `service.name = catalog-service` và `deployment.environment = prod`. Log hiện tại là text, level được tách từ text.
Nếu không thấy log: `sudo docker logs signoz-ingester 2>&1 | tail -50` (lỗi operator `filelog/docker`), và kiểm tra log Docker có `attrs`:
`sudo tail -1 /var/lib/docker/containers/$(docker inspect -f '{{.Id}}' prod-catalog-service-1)/*-json.log`.

## Nâng version
Đổi `x-versions` trong compose (SigNoz cùng collector theo release notes) → PR → merge → lặp lại bước 1, rồi
`sudo docker compose -f compose.observability.yml up -d --force-recreate --wait`. Phải tạo lại cả stack, không restart lẻ từng service.

## Dừng / gỡ
```bash
ssh unbutu 'cd /opt/unbutu-platform/observability && sudo docker compose -f compose.observability.yml down'   # giữ dữ liệu
ssh unbutu 'sudo tailscale serve --https=8443 off'
```
**Không dùng `down -v`** trừ khi chủ ý xoá toàn bộ dữ liệu telemetry. Gỡ SigNoz không ảnh hưởng prod: log vẫn có trong `docker logs`.
