// Kịch bản "Xem sự kiện" – CHỈ ĐỌC (không tạo đơn, an toàn cho prod).
// Mỗi lượt = đúng 1 request (để "rps" = số request/giây), chọn ngẫu nhiên theo tỉ lệ giống người dùng thật:
//   40% danh sách sự kiện · 20% chi tiết · 20% hạng vé · 20% "Còn x / y" (inventory, Redis)
// Gọi thẳng api-gateway trong mạng Docker (không qua Funnel/CDN) → đo năng lực của hệ thống, không đo mạng.
import http from 'k6/http';
import { check } from 'k6';
import { buildOptions, summary } from './lib/steps.js';

const BASE = __ENV.BASE_URL || 'http://api-gateway:8080';

export const options = buildOptions();

export function setup() {
  const res = http.get(`${BASE}/api/catalog/events`, { responseType: 'text', tags: { name: 'setup' } });
  if (res.status !== 200) throw new Error(`Không lấy được danh sách sự kiện: HTTP ${res.status} từ ${BASE}`);
  const ids = (res.json('items') || []).map((e) => e.id);
  if (!ids.length) throw new Error('Chưa có sự kiện nào đang bán – tạo 1 sự kiện trong /admin trước');
  console.log(`Kịch bản browse: ${ids.length} sự kiện, gọi ${BASE}`);
  return { ids };
}

// tags.name gom URL có id về 1 dòng (không nổ số series trên dashboard).
const ROUTES = [
  [0.4, () => '/api/catalog/events', 'GET /api/catalog/events'],
  [0.6, (id) => `/api/catalog/events/${id}`, 'GET /api/catalog/events/{id}'],
  [0.8, (id) => `/api/catalog/events/${id}/ticket-types`, 'GET /api/catalog/events/{id}/ticket-types'],
  [1.0, (id) => `/api/inventory/events/${id}/availability`, 'GET /api/inventory/events/{id}/availability'],
];

export default function (data) {
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const r = Math.random();
  const [, path, name] = ROUTES.find(([p]) => r < p);
  const res = http.get(`${BASE}${path(id)}`, { tags: { name } });
  check(res, { 'HTTP 200': (x) => x.status === 200 });
}

export const handleSummary = summary;
