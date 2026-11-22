// Gọi API cùng origin (/api/** → api-gateway). Access token chỉ giữ trong bộ nhớ; refresh token là cookie
// HttpOnly do identity-service đặt (JS không đọc được).

export type User = {
  id: string;
  email: string;
  fullName: string | null;
  phone: string | null;
  roles: string[];
  emailVerified: boolean;
  hasPassword: boolean;
};

export type TokenResponse = { accessToken: string; tokenType: string; expiresIn: number; user: User };

/** Lỗi từ backend (RFC 9457 Problem Details + code). */
export class ApiError extends Error {
  constructor(public status: number, public code: string | undefined, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const res = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  if (!res.ok) {
    let code: string | undefined;
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      code = body.code;
      message = body.detail ?? body.title ?? message;
    } catch {
      /* không phải JSON */
    }
    throw new ApiError(res.status, code, message);
  }
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
}

export const api = {
  providers: () => request<{ password: boolean; google: boolean }>('/api/identity/providers'),
  login: (email: string, password: string, rememberMe: boolean) =>
    request<TokenResponse>('/api/identity/login', { method: 'POST', body: JSON.stringify({ email, password, rememberMe }) }),
  register: (email: string, password: string, fullName: string) =>
    request<TokenResponse>('/api/identity/register', { method: 'POST', body: JSON.stringify({ email, password, fullName }) }),
  refresh: () => request<TokenResponse>('/api/identity/refresh', { method: 'POST' }),
  logout: () => request<void>('/api/identity/logout', { method: 'POST' }),
};

// ---- catalog: sự kiện + hạng vé ----

export type EventType = 'MOVIE' | 'CONCERT' | 'SPORT' | 'THEATER';
export type CatalogEvent = {
  id: string;
  title: string;
  type: EventType;
  description: string | null;
  venue: string | null;
  startsAt: string | null; // ISO-8601 (UTC)
  hidden: boolean;          // ẩn khỏi danh sách của khách (vd. sự kiện load test)
  updatedAt: string;
};
export type EventInput = Omit<CatalogEvent, 'id' | 'updatedAt'>;

export type TicketStatus = 'DRAFT' | 'ON_SALE' | 'PAUSED';
export type TicketType = {
  id: string;
  code: string;
  eventId: string;
  eventTitle: string;
  name: string;
  description: string | null;
  price: number; // VND
  totalQuantity: number;
  status: TicketStatus;
  /** false: đã từng mở bán → chỉ tạm dừng được, không xoá hẳn. */
  deletable: boolean;
  updatedAt: string;
};
export type TicketTypeInput = Pick<TicketType, 'eventId' | 'name' | 'description' | 'price' | 'totalQuantity' | 'status'>;

/** Thẻ sự kiện trên trang danh sách (khách). */
export type EventSummary = Pick<CatalogEvent, 'id' | 'title' | 'type' | 'venue' | 'startsAt'> & { priceFrom: number };
export type Slice<T> = { items: T[]; page: number; size: number; hasNext: boolean };
/** Hạng vé khách thấy (ON_SALE | PAUSED). */
export type PublicTicketType = Pick<TicketType, 'id' | 'name' | 'description' | 'price' | 'totalQuantity' | 'status'>;

const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) });

export const catalog = {
  // Công khai – KHÔNG gửi token (response giống nhau cho mọi người → CDN cache được).
  browse: (page: number, size = 12) => request<Slice<EventSummary>>(`/api/catalog/events?page=${page}&size=${size}`),
  event: (id: string) => request<CatalogEvent>(`/api/catalog/events/${id}`),
  eventTicketTypes: (id: string) => request<PublicTicketType[]>(`/api/catalog/events/${id}/ticket-types`),

  // Admin
  events: (t: string) => request<CatalogEvent[]>('/api/catalog/admin/events', {}, t),
  createEvent: (t: string, e: EventInput) => request<CatalogEvent>('/api/catalog/admin/events', json('POST', e), t),
  updateEvent: (t: string, id: string, e: EventInput) => request<CatalogEvent>(`/api/catalog/admin/events/${id}`, json('PUT', e), t),
  deleteEvent: (t: string, id: string) => request<void>(`/api/catalog/admin/events/${id}`, json('DELETE'), t),

  ticketTypes: (t: string) => request<TicketType[]>('/api/catalog/admin/ticket-types', {}, t),
  createTicketType: (t: string, x: TicketTypeInput) => request<TicketType>('/api/catalog/admin/ticket-types', json('POST', x), t),
  updateTicketType: (t: string, id: string, x: TicketTypeInput) =>
    request<TicketType>(`/api/catalog/admin/ticket-types/${id}`, json('PUT', x), t),
  deleteTicketType: (t: string, id: string) => request<void>(`/api/catalog/admin/ticket-types/${id}`, json('DELETE'), t),
};

// ---- inventory: tồn kho realtime (A8b) ----
export type StockState = 'ON_SALE' | 'LOW' | 'SOLD_OUT' | 'PAUSED';
export type Availability = { ticketTypeId: string; available: number; total: number; state: StockState };

export const inventory = {
  // Công khai, cache CDN 1 s – không gửi token.
  availability: (eventId: string) => request<Availability[]>(`/api/inventory/events/${eventId}/availability`),
};

// ---- booking: giữ vé 10′ (A9, A11–A13) ----
export type BookingStatus = 'PENDING' | 'CANCELLED' | 'EXPIRED' | 'AWAITING_PAYMENT' | 'CONFIRMED' | 'REFUND_PENDING' | 'REFUNDED';
export type Booking = {
  id: string;
  code: string;
  status: BookingStatus;
  cancelReason: string | null;
  event: { id: string; title: string | null; venue: string | null; startsAt: string | null };
  lines: { ticketTypeId: string; name: string; unitPrice: number; quantity: number; amount: number }[];
  ticketCount: number;
  subtotal: number;
  fee: number;
  total: number;
  expiresAt: string;
  createdAt: string;
};

export const bookings = {
  /** idempotencyKey: sinh 1 lần cho 1 lần bấm, gửi lại y nguyên khi retry → server trả cùng đơn. */
  create: (t: string, idempotencyKey: string, eventId: string, items: { ticketTypeId: string; quantity: number }[]) =>
    request<Booking>('/api/bookings', { ...json('POST', { eventId, items }), headers: { 'Idempotency-Key': idempotencyKey } }, t),
  get: (t: string, id: string) => request<Booking>(`/api/bookings/${id}`, {}, t),
  /** A12 – lịch sử đơn, mới trước (Slice). */
  mine: (t: string, page = 0, size = 10) => request<Slice<Booking>>(`/api/bookings/me?page=${page}&size=${size}`, {}, t),
  cancel: (t: string, id: string) => request<void>(`/api/bookings/${id}`, json('DELETE'), t),
  /** A10 – trả URL cổng thanh toán; bấm lại → cùng URL. */
  checkout: (t: string, id: string, method: string, buyer: { name: string; email: string; phone: string }) =>
    request<{ paymentUrl: string }>(`/api/bookings/${id}/checkout`, json('POST', { method, buyer }), t),
};

// ---- dashboard admin (ADM-1) – booking-service, tiền VND, ngày theo giờ VN ----
export type AdminStats = {
  from: string;
  to: string;
  eventId: string | null;
  includeTest: boolean;
  generatedAt: string;
  kpi: {
    totalOrders: number; confirmedOrders: number; openOrders: number; failedOrders: number; refundOrders: number;
    ticketsSold: number; netRevenue: number; grossRevenue: number; refundAmount: number;
    conversionRate: number; avgOrderValue: number;
  };
  byStatus: Partial<Record<BookingStatus, { orders: number; tickets: number; amount: number }>>;
  cancelReasons: Record<string, number>;
  daily: { date: string; orders: number; confirmedOrders: number; ticketsSold: number; netRevenue: number }[];
  topEvents: { eventId: string; title: string | null; startsAt: string | null; hidden: boolean; confirmedOrders: number; ticketsSold: number; netRevenue: number }[];
};

export const adminStats = {
  get: (t: string, q: { from: string; to: string; eventId?: string; includeTest: boolean }) => {
    const p = new URLSearchParams({ from: q.from, to: q.to, includeTest: String(q.includeTest) });
    if (q.eventId) p.set('eventId', q.eventId);
    return request<AdminStats>(`/api/bookings/admin/stats?${p}`, {}, t);
  },
};

// ---- tra cứu đơn + thanh toán cho admin (ADM-2, ADM-3) – keyset: nextCursor null = hết ----
export type CursorPage<T> = { items: T[]; nextCursor: string | null };
export type AdminBooking = {
  id: string; code: string; status: BookingStatus; cancelReason: string | null; userId: string;
  event: { id: string; title: string | null; startsAt: string | null; hidden: boolean };
  buyer: { name: string | null; email: string | null; phone: string | null };
  ticketCount: number; subtotal: number; fee: number; total: number; paymentMethod: string | null;
  createdAt: string; expiresAt: string; confirmedAt: string | null; updatedAt: string;
};
export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED';
export type AdminPayment = {
  id: string; bookingId: string; bookingCode: string; attempt: number; txnRef: string; amount: number; method: string;
  status: PaymentStatus; stuck: boolean; providerTxnNo: string | null; providerResponseCode: string | null;
  expireAt: string; paidAt: string | null; refundedAt: string | null; createdAt: string; updatedAt: string;
  lastCheck: { gatewayCode: string | null; gatewayStatus: string | null; action: string; note: string | null; checkedAt: string } | null;
};
export type AdminBookingDetail = {
  booking: AdminBooking;
  lines: { ticketTypeId: string; name: string; unitPrice: number; quantity: number; amount: number }[];
  payments: AdminPayment[] | null;
  paymentsUnavailable: boolean;
};
export type PaymentSummary = {
  from: string; to: string; generatedAt: string; stuck: number;
  countByStatus: Partial<Record<PaymentStatus, number>>; amountByStatus: Partial<Record<PaymentStatus, number>>;
  failureCodes: Record<string, number>;
};

const qs = (q: Record<string, string | number | boolean | undefined | null>) => {
  const p = new URLSearchParams();
  Object.entries(q).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.set(k, String(v)); });
  return p.toString();
};

export const adminOrders = {
  list: (t: string, q: { status?: string; eventId?: string; q?: string; includeTest?: boolean; cursor?: string | null; size?: number }) =>
    request<CursorPage<AdminBooking>>(`/api/bookings/admin/bookings?${qs(q)}`, {}, t),
  detail: (t: string, id: string) => request<AdminBookingDetail>(`/api/bookings/admin/bookings/${id}`, {}, t),
  payments: (t: string, q: { status?: string; stuck?: boolean; q?: string; cursor?: string | null; size?: number }) =>
    request<CursorPage<AdminPayment>>(`/api/bookings/admin/payments?${qs(q)}`, {}, t),
  paymentSummary: (t: string, from: string, to: string) =>
    request<PaymentSummary>(`/api/bookings/admin/payments/summary?${qs({ from, to })}`, {}, t),
};

// ---- vé điện tử (notification-service, ADR-0009) ----
export type Ticket = {
  id: string;
  bookingId: string;
  bookingCode: string;
  seq: number;
  eventId: string;
  eventTitle: string | null;
  venue: string | null;
  startsAt: string | null;
  ticketTypeId: string;
  ticketTypeName: string;
  unitPrice: number;
  status: 'VALID' | 'USED' | 'VOID';
  qr: string; // JWS ES256 – vẽ thành mã QR, cổng soát vé verify chữ ký
  issuedAt: string;
};

export const tickets = {
  /** Rỗng = chưa phát (vé phát bất đồng bộ ngay sau CONFIRMED) → poll. */
  ofBooking: (t: string, bookingId: string) =>
    request<{ bookingId: string; tickets: Ticket[] }>(`/api/tickets/bookings/${bookingId}`, {}, t),
};

/** Bắt đầu login Google: chuyển cả trang sang backend → Google → quay về /auth/callback. */
export const GOOGLE_LOGIN_URL = '/api/identity/oauth2/authorization/google';

/** Thông báo tiếng Việt theo `code` của backend. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    switch (e.code) {
      case 'INVALID_CREDENTIALS': return 'Email hoặc mật khẩu không đúng.';
      case 'EMAIL_TAKEN': return 'Email này đã được đăng ký. Hãy đăng nhập.';
      case 'GOOGLE_EMAIL_NOT_VERIFIED': return 'Email Google của bạn chưa được xác minh.';
      case 'ACCOUNT_DISABLED': return 'Tài khoản đã bị khoá.';
      case 'GOOGLE_LOGIN_FAILED': return 'Đăng nhập Google không thành công. Vui lòng thử lại.';
      case 'TICKET_TYPE_NAME_TAKEN': return 'Sự kiện này đã có hạng vé cùng tên.';
      case 'TICKET_TYPE_ALREADY_PUBLISHED': return e.message;
      case 'EVENT_HAS_TICKET_TYPES': return 'Sự kiện còn hạng vé – hãy xoá các hạng vé trước.';
      case 'EVENT_NOT_FOUND': return 'Sự kiện không tồn tại hoặc đã bị gỡ.';
      case 'TICKET_TYPE_NOT_FOUND': return 'Hạng vé không còn tồn tại. Hãy tải lại trang.';
      case 'CONFLICT': return 'Dữ liệu vừa bị thay đổi, hãy tải lại trang.';
      case 'SOLD_OUT': return 'Rất tiếc, hạng vé bạn chọn vừa hết hoặc không còn đủ số lượng.';
      case 'NOT_ON_SALE': return 'Hạng vé bạn chọn hiện không mở bán.';
      case 'LIMIT_EXCEEDED': return e.message;
      case 'INVENTORY_UNAVAILABLE': return 'Hệ thống giữ vé đang bận, vui lòng thử lại sau ít giây.';
      case 'BOOKING_NOT_FOUND': return 'Không tìm thấy đơn hàng.';
      case 'BOOKING_NOT_CANCELLABLE': return 'Đơn hàng không còn huỷ được.';
      case 'HOLD_EXPIRED': return 'Đã hết thời gian giữ vé. Vui lòng chọn vé lại.';
      case 'ALREADY_PAID': return 'Đơn hàng này đã được thanh toán.';
      case 'PAYMENT_UNAVAILABLE': return 'Cổng thanh toán đang bận, vui lòng thử lại.';
    }
    if (e.status === 401) return 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.';
    if (e.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
    if (e.status === 400) return 'Thông tin chưa hợp lệ, vui lòng kiểm tra lại.';
    if (e.status >= 500) return 'Hệ thống đang bận, vui lòng thử lại sau.';
    return e.message;
  }
  return 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.';
}

// ---- load test (chỉ ADMIN, loadtest-service) ----

export type LtScenario = { name: string; title: string; description: string; writes: boolean; unit: string; maxDurationSeconds: number };
export type LtConfig = { scenarios: LtScenario[]; limits: { maxRps: number; maxDurationSeconds: number } };
export type LtRunRequest = { scenario: string; steps: number[]; stepSeconds: number; p95Ms: number; maxErrPct: number; stopOnFail: boolean };
export type LtStatus = 'RUNNING' | 'COMPLETED' | 'STOPPED' | 'ERROR' | 'ABORTED';
export type LtRun = LtRunRequest & {
  id: string; status: LtStatus; startedBy: string; startedAt: string; finishedAt: string | null; passUpToRps: number | null; eventId: string | null;
};
export type LtStep = {
  rps: number; state: 'PENDING' | 'RUNNING' | 'DONE' | 'SKIPPED'; reqs: number; achievedRps: number | null;
  p95: number | null; p99: number | null; errPct: number | null; dropped: number; pass: boolean | null;
  ttqP95: number | null; ticketPct: number | null; // chỉ kịch bản mua vé
};
/** 1 điểm / 2 s. rps = lượt/giây, httpRps = request/giây. p95/errPct/ttqP95: dồn từ đầu bậc đang chạy. */
export type LtSample = { t: number; rps: number; httpRps: number; vus: number; stepRps: number; p95: number | null; p99: number | null; errPct: number; ttqP95: number | null };
export type LtDetail = { run: LtRun; stepViews: LtStep[]; samples: LtSample[]; logTail: string | null };

export const loadtest = {
  config: (t: string) => request<LtConfig>('/api/loadtest/config', {}, t),
  runs: (t: string) => request<LtRun[]>('/api/loadtest/runs', {}, t),
  current: (t: string) => request<LtDetail | undefined>('/api/loadtest/runs/current', {}, t),
  run: (t: string, id: string) => request<LtDetail>(`/api/loadtest/runs/${id}`, {}, t),
  start: (t: string, r: LtRunRequest) => request<LtRun>('/api/loadtest/runs', json('POST', r), t),
  stop: (t: string, id: string) => request<void>(`/api/loadtest/runs/${id}/stop`, json('POST'), t),
};
