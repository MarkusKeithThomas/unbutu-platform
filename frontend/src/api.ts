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
    }
    if (e.status === 401) return 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.';
    if (e.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
    if (e.status === 400) return 'Thông tin chưa hợp lệ, vui lòng kiểm tra lại.';
    if (e.status >= 500) return 'Hệ thống đang bận, vui lòng thử lại sau.';
    return e.message;
  }
  return 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.';
}
