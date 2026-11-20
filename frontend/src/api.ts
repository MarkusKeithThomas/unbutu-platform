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

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers },
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
    }
    if (e.status === 400) return 'Thông tin chưa hợp lệ, vui lòng kiểm tra lại.';
    if (e.status >= 500) return 'Hệ thống đang bận, vui lòng thử lại sau.';
    return e.message;
  }
  return 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.';
}
