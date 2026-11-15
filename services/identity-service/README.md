# identity-service

**Người dùng & xác thực**

| | |
|---|---|
| Trách nhiệm | Đăng ký, đăng nhập, refresh token, phát JWT (RS256, công bố JWKS cho gateway), hồ sơ người dùng, role (CUSTOMER, ORGANIZER, ADMIN). |
| Dữ liệu | Postgres `identity_db`: users, roles, refresh_tokens. |
| Event (RabbitMQ) | Phát: `user.registered`. |
| Gọi đồng bộ | – |
| Package | `com.unbutu.ticket.identity` |
| Port trong container | 8080 |

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
