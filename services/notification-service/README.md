# notification-service

**Vé điện tử QR** (ADR-0009) · sau này: gửi email/Telegram.

| | |
|---|---|
| Trách nhiệm | Phát vé điện tử (QR ký ES256) khi đơn `CONFIRMED`; API vé cho khách. Sau: email vé. |
| Dữ liệu | Postgres `notification_db`: `tickets` (bất biến sau khi phát). |
| Event (Redpanda) | Nghe: `booking.event.v1` (`booking.confirmed`), group `notification-service`, static membership `notification-1-booking`. |
| Gọi đồng bộ | – (event mang đủ dữ liệu, không gọi ngược booking) |
| Package | `com.unbutu.ticket.notification` (vé: `.ticket` – tách được thành ticketing-service) |
| Port | container 8080 · local 8086 |

## API (qua gateway `/api/tickets/**`)
| Endpoint | Auth | Mô tả |
|---|---|---|
| `GET /api/tickets/bookings/{bookingId}` | JWT (chủ đơn) | Vé của 1 đơn. Rỗng = chưa phát → UI poll. Có vé → `Cache-Control: private, max-age=300` |
| `GET /api/tickets/me?page&size` | JWT | Vé của tôi, mới trước (Slice) |
| `GET /api/tickets/.well-known/jwks.json` | công khai | Public key verify QR (app soát vé) |

- Phát vé: 1 transaction / đơn; id vé = UUID theo tên `bookingId:seq` + `UNIQUE (booking_id, seq)` → event lặp không sinh vé thứ 2.
- QR = JWS `{tid, eid, tt, iat}`, header `kid`. Env: `QR_SIGNING_KEY` (EC P-256 DER base64, PKCS#8 hoặc SEC1:
  `openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -outform DER | base64`), `QR_KEY_ID` (mặc định `qr-1`).
  Khởi động ký thử + verify → khoá sai thì service không lên.

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
