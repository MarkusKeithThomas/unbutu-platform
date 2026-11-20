import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, ApiError, type TokenResponse, type User } from './api';

type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: User; accessToken: string };

type AuthContextValue = {
  state: AuthState;
  login: (email: string, password: string, rememberMe: boolean) => Promise<void>;
  register: (email: string, password: string, fullName: string) => Promise<void>;
  /** Lấy access token từ refresh cookie (khi mở trang, sau login Google). */
  restore: () => Promise<boolean>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

// Mỗi refresh token chỉ dùng được 1 lần (xoay vòng; dùng lại = bị coi là đánh cắp → backend thu hồi cả phiên).
// → Gộp mọi lời gọi refresh đồng thời (React StrictMode gọi effect 2 lần, nhiều component…) thành 1 request.
let refreshInFlight: Promise<TokenResponse | null> | null = null;
function refreshOnce(): Promise<TokenResponse | null> {
  refreshInFlight ??= api
    .refresh()
    .catch((e) => {
      if (e instanceof ApiError && e.status === 401) return null;
      throw e;
    })
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  const apply = useCallback((t: TokenResponse | null) => {
    setState(t ? { status: 'authenticated', user: t.user, accessToken: t.accessToken } : { status: 'anonymous' });
  }, []);

  const restore = useCallback(async () => {
    try {
      const t = await refreshOnce();
      apply(t);
      return t !== null;
    } catch {
      apply(null);
      return false;
    }
  }, [apply]);

  useEffect(() => {
    void restore();
  }, [restore]);

  // Access token sống 15′ → làm mới trước khi hết hạn 1 phút.
  useEffect(() => {
    if (state.status !== 'authenticated') return;
    const id = window.setTimeout(() => void restore(), 14 * 60 * 1000);
    return () => window.clearTimeout(id);
  }, [state, restore]);

  const value: AuthContextValue = {
    state,
    restore,
    login: async (email, password, rememberMe) => apply(await api.login(email, password, rememberMe)),
    register: async (email, password, fullName) => apply(await api.register(email, password, fullName)),
    logout: async () => {
      try {
        await api.logout();
      } finally {
        apply(null);
      }
    },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth phải nằm trong <AuthProvider>');
  return ctx;
}
