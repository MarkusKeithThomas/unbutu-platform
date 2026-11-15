# api-gateway

**Cổng vào duy nhất (Spring Cloud Gateway, WebFlux)**

| | |
|---|---|
| Trách nhiệm | Routing `/api/**`, sinh/propagate `X-Request-Id` + `traceparent`, xác thực JWT tại biên, rate limit (Redis), timeout, circuit breaker, CORS, chuẩn hoá lỗi. |
| Dữ liệu | Không có DB. Redis (rate limit, blacklist JWT). |
| Event (RabbitMQ) | – |
| Gọi đồng bộ | Gọi tới mọi service bên dưới. |
| Package | `com.unbutu.ticket.gateway` |
| Port trong container | 8080 |

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
