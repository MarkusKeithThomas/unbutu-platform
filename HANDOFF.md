# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-16.

## Làm ngay
1. **OBS-3 – access log ở gateway** (`docs/tasks/observability.md`). Không phụ thuộc gì; nhánh mới từ `main`.
2. **OBS-7 – đo lại CPU của ClickHouse** (sau OBS-2 là ~23% khi rảnh). Xem trên SigNoz: `container.cpu.*`,
   `compose.service = signoz-telemetrystore-clickhouse-0-0`. Nếu vẫn > 5% → giảm system log trong `deploy/signoz/clickhouse/config.yaml`.

## Đang chờ người dùng
- Merge PR docs `docs/obs-2-done`.
- **OBS-4:** chốt cách đo đạc: Micrometer + OTLP (đề xuất) hay OpenTelemetry Java agent.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
