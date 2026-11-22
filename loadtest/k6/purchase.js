// Kịch bản "Mua vé end-to-end" – CÓ GHI DỮ LIỆU (đơn, giao dịch giả lập, vé QR) → chỉ chạy trên sự kiện load test ẨN.
// Mỗi lượt = 1 khách mua 1 vé, đủ luồng thật:
//   giữ vé (booking → inventory Redis/Lua) → checkout (payment) → trang ngân hàng giả lập → "thanh toán thành công"
//   → [bất đồng bộ: IPN → payment.result.v1 → booking CONFIRMED → booking.event.v1 → notification phát QR]
//   → hỏi /api/tickets/bookings/{id} tới khi có QR.
// Đo: p95 từng bước (tags.name) + time_to_ticket (bấm mua → có QR) + ticket_issued (% đơn có QR trong QR_WAIT_MS).
//
// Biến (loadtest-service truyền khi bấm chạy – tự tạo sự kiện ẩn + bật cờ bỏ giới hạn 4 vé cho đúng sự kiện đó):
//   EVENT_ID, TICKET_TYPE_ID  sự kiện + hạng vé load test
//   LT_EMAIL, LT_PASSWORD     1 tài khoản test (mua song song nhiều luồng – cờ limit-bypass của booking)
//   QR_WAIT_MS (30000)  chờ QR tối đa · QR_P95_MS (5000)  ngưỡng p95 "tới lúc có QR" để bậc PASS
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Rate, Trend } from 'k6/metrics';
import { buildOptions, summary } from './lib/steps.js';

const BASE = __ENV.BASE_URL || 'http://api-gateway:8080';
const EVENT_ID = __ENV.EVENT_ID;
const TICKET_TYPE_ID = __ENV.TICKET_TYPE_ID;
const QR_WAIT_MS = Number(__ENV.QR_WAIT_MS || 30000);
const QR_P95_MS = Number(__ENV.QR_P95_MS || 5000);

const timeToTicket = new Trend('time_to_ticket', true);
const ticketIssued = new Rate('ticket_issued');

export const options = buildOptions({
  iterSeconds: 3, // giữ + checkout + thanh toán (~0,2 s) + chờ IPN/Kafka/QR (~1–3 s)
  extraThresholds: (sel) => ({
    [`ticket_issued${sel}`]: [`rate>0.99`],
    [`time_to_ticket${sel}`]: [`p(95)<${QR_P95_MS}`],
  }),
});

const JSON_HDR = { 'Content-Type': 'application/json' };

function login() {
  const res = http.post(`${BASE}/api/identity/login`, JSON.stringify({ email: __ENV.LT_EMAIL, password: __ENV.LT_PASSWORD }),
    { headers: JSON_HDR, responseType: 'text', tags: { name: 'setup' } });
  if (res.status !== 200) throw new Error(`Đăng nhập tài khoản test thất bại: HTTP ${res.status}`);
  return res.json('accessToken');
}

function hold(auth, key, tag) {
  return http.post(`${BASE}/api/bookings`,
    JSON.stringify({ eventId: EVENT_ID, items: [{ ticketTypeId: TICKET_TYPE_ID, quantity: 1 }] }),
    { headers: { ...JSON_HDR, ...auth, 'Idempotency-Key': key }, responseType: 'text', tags: { name: tag } });
}

export function setup() {
  if (!EVENT_ID || !TICKET_TYPE_ID || !__ENV.LT_EMAIL || !__ENV.LT_PASSWORD) {
    throw new Error('Thiếu EVENT_ID / TICKET_TYPE_ID / LT_EMAIL / LT_PASSWORD (chạy qua loadtest-service)');
  }
  const token = login();
  const auth = { Authorization: `Bearer ${token}` };
  // Sự kiện vừa tạo: inventory (tồn kho Redis) + booking (bảng giá) nhận qua Kafka → chờ tới khi giữ thử được 1 vé.
  for (let i = 0; i < 45; i++) {
    const r = hold(auth, `lt-probe-${Date.now()}`, 'setup');
    if (r.status === 201) {
      http.del(`${BASE}/api/bookings/${r.json('id')}`, null, { headers: auth, tags: { name: 'setup' } });
      console.log(`Kịch bản purchase: sự kiện ${EVENT_ID} sẵn sàng sau ${i * 2}s, gọi ${BASE}`);
      return { token };
    }
    sleep(2);
  }
  throw new Error('Sự kiện load test chưa sẵn sàng sau 90 s (inventory/booking chưa nhận catalog.event.v1?)');
}

const unescapeHtml = (s) => s.replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>')
  .replace(/&amp;/g, '&');

export default function (data) {
  const auth = { Authorization: `Bearer ${data.token}` };
  const t0 = Date.now();
  const key = `lt${exec.vu.idInTest}x${exec.vu.iterationInInstance}x${t0}`;

  let r = hold(auth, key, 'POST /api/bookings (giữ vé)');
  if (!check(r, { 'giữ vé 201': (x) => x.status === 201 })) { ticketIssued.add(false); return; }
  const id = r.json('id');

  r = http.post(`${BASE}/api/bookings/${id}/checkout`,
    JSON.stringify({ method: 'VNBANK', buyer: { name: 'Load Test', email: __ENV.LT_EMAIL } }),
    { headers: { ...JSON_HDR, ...auth }, responseType: 'text', tags: { name: 'POST /api/bookings/{id}/checkout' } });
  if (!check(r, { 'checkout 200': (x) => x.status === 200 })) { ticketIssued.add(false); return; }
  // paymentUrl trỏ domain công khai → đổi sang BASE (gọi thẳng gateway trong mạng Docker).
  const payUrl = String(r.json('paymentUrl')).replace(/^https?:\/\/[^/]+/, BASE);

  r = http.get(payUrl, { responseType: 'text', tags: { name: 'GET /api/payments/mock/pay (trang ngân hàng)' } });
  const q = /name="q" value="([^"]*)"/.exec(r.body || '');
  if (!check(r, { 'trang ngân hàng 200': (x) => x.status === 200 && !!q })) { ticketIssued.add(false); return; }

  r = http.post(payUrl.split('?')[0], { q: unescapeHtml(q[1]), action: 'success' },
    { redirects: 0, tags: { name: 'POST /api/payments/mock/pay (thanh toán)' } });
  if (!check(r, { 'thanh toán 302': (x) => x.status === 302 })) { ticketIssued.add(false); return; }

  // Bất đồng bộ: IPN → Kafka → CONFIRMED → QR. Hỏi lại mỗi 0,5 s.
  while (Date.now() - t0 < QR_WAIT_MS) {
    sleep(0.5);
    r = http.get(`${BASE}/api/tickets/bookings/${id}`,
      { headers: auth, responseType: 'text', tags: { name: 'GET /api/tickets/bookings/{id} (chờ QR)' } });
    if (r.status === 200 && (r.json('tickets') || []).length > 0) {
      timeToTicket.add(Date.now() - t0);
      ticketIssued.add(true);
      return;
    }
  }
  ticketIssued.add(false);
}

export const handleSummary = summary;
