# unbutu-platform – Hệ thống đặt vé (microservice)

Dự án luyện kỹ năng backend senior: microservice Spring Boot, CI/CD, observability (ELK), phân quyền, API Gateway.

- Kiến trúc & hạ tầng: [ARCHITECTURE.md](ARCHITECTURE.md)
- Quyết định kiến trúc: [docs/adr/](docs/adr/)
- Từng service: `services/<tên>/README.md`

## Chạy local

**Cần có:** Java 21, Docker Desktop đang chạy, `make`, IntelliJ.

Mỗi service có **1 port cố định** trên máy, giống nhau dù chạy trong container hay trong IntelliJ:

| Service | Port | | Service | Port |
|---|---|---|---|---|
| api-gateway | 8080 | | booking-service | 8084 |
| identity-service | 8081 | | payment-service | 8085 |
| catalog-service | 8082 | | notification-service | 8086 |
| inventory-service | 8083 | | postgres | 5432 |

### Chạy toàn bộ trong container
```bash
make up                      # build jar + image, chạy tất cả
make up s=catalog-service    # chỉ 1 service (+ hạ tầng nó cần)
make restart s=catalog-service   # sau khi sửa code
make logs s=catalog-service · make ps · make down · make clean (xoá cả dữ liệu DB)
make build                   # build + toàn bộ test (như CI)
```

### Debug 1 service trong IntelliJ (các service khác vẫn trong container)
1. `make ide s=booking-service` – chạy mọi thứ trong container **trừ** booking-service; gateway được trỏ sang
   `host.docker.internal:8084` (bản chạy trong IntelliJ). Nhiều service: `make ide s="booking-service payment-service"`.
2. IntelliJ → chạy **Debug** `BookingServiceApplication` với biến môi trường `SPRING_PROFILES_ACTIVE=local`
   (lần đầu: Run → Edit Configurations → Spring Boot/Application → Environment variables).
   Profile `local` (`application-local.yml`) dùng port cố định, DB `localhost:5432`, service khác ở `localhost:<port>`.
3. Đặt breakpoint, gọi qua gateway `curl localhost:8080/api/...` hoặc gọi thẳng `localhost:8084`.
   Sửa code → Rerun trong IntelliJ (không cần build image).
4. Xong: `make up` để đưa service về lại container.

### Dữ liệu mẫu
Chỉ có ở local và integration test (`db/seed-local/`, Flyway nạp khi profile `local` hoặc qua `compose.local.yml`); prod khởi động với bảng rỗng.
Nếu DB local tạo trước 2026-11-15 báo lỗi checksum Flyway → `make clean && make up` một lần.

### Database
`localhost:5432` · mỗi service 1 database + user riêng, vd. `catalog_db` / user `catalog` / mật khẩu `catalog`.
Bảng do Flyway của từng service tạo (`src/main/resources/db/migration/V<n>__<mô tả>.sql`) khi service khởi động.

## Deploy prod
Không deploy tay: merge `main` → CI → Release → **Approve** trong tab Actions (environment `production`). Rollback: Actions → Deploy → Run workflow với SHA cũ.
Các bước đã setup: [docs/setup-steps.md](docs/setup-steps.md) · cấu hình/secret prod: repo private `pro-ticket-workload` (ADR-0004).
