# ADR-0001: Dùng Docker Compose thay vì k3s/k8s

- Trạng thái: Chấp nhận · 2026-11-15

## Bối cảnh
Chạy trên 1 máy (unbutu, 11 GiB RAM). Mục tiêu là luyện CI/CD, observability, phân quyền, gateway – không muốn tốn thời gian vận hành Kubernetes.

## Quyết định
Docker Compose, tách môi trường bằng compose project (`prod`, `staging`, `observability`).

> Cập nhật 2026-11-15: bỏ `staging` – xem ADR-0003.

## Hệ quả
- (+) Nhẹ, đơn giản, đã có sẵn; chuyển lên VPS dễ.
- (−) Không tự phục hồi khi máy chết, không autoscale, rolling update hạn chế.
- Giữ quy ước (1 service = 1 container, gọi bằng tên, healthcheck, cấu hình qua env) để có thể viết manifest k3s sau mà không sửa code.
