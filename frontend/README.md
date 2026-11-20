# frontend

UI TicketHub – **React 18 + Vite + TypeScript** (design: `private-docs/ui-html`). Chạy trên unbutu trong container
`frontend` (nginx, port 8080 nội bộ); **api-gateway** chuyển mọi path không phải `/api/**` vào đây → UI và API cùng
origin (`https://unbutu.tail3c11f4.ts.net`), cookie refresh (`Path=/api/identity`, HttpOnly) hoạt động không cần CORS.

| Lệnh | |
|---|---|
| `npm install` | lần đầu |
| `npm run dev` | http://localhost:5173, `/api` proxy tới gateway local (`make up` trước) |
| `npm run build` | ra `dist/` (CI kiểm tra build trong Docker) |

Trang: `/login`, `/register`, `/auth/callback` (sau login Google), `/` (cần đăng nhập).
Phiên đăng nhập: access token chỉ trong bộ nhớ; mở lại trang → `POST /api/identity/refresh` (cookie) để khôi phục.
Mọi lời gọi refresh đồng thời được gộp làm 1 (refresh token xoay vòng, dùng lại sẽ bị thu hồi cả phiên).
