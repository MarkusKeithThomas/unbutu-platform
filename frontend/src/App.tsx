import type { ReactElement } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { EventsPage } from './admin/EventsPage';
import { TicketTypesPage } from './admin/TicketTypesPage';
import { useAuth } from './auth';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';

function Loading() {
  return <div className="center">Đang tải…</div>;
}

/** Chỉ cho người đã đăng nhập; chưa → /login. */
function RequireAuth({ children }: { children: ReactElement }) {
  const { state } = useAuth();
  if (state.status === 'loading') return <Loading />;
  return state.status === 'authenticated' ? children : <Navigate to="/login" replace />;
}

/** Chỉ cho ADMIN (API catalog cũng kiểm tra lại – đây chỉ để không hiện màn không dùng được). */
function RequireAdmin({ children }: { children: ReactElement }) {
  const { state } = useAuth();
  if (state.status === 'loading') return <Loading />;
  if (state.status !== 'authenticated') return <Navigate to="/login" replace />;
  if (!state.user.roles.includes('ADMIN')) {
    return (
      <div className="center" style={{ flexDirection: 'column', gap: 8 }}>
        <p>Tài khoản {state.user.email} không có quyền quản trị.</p>
        <Link to="/">Về trang chủ</Link>
      </div>
    );
  }
  return children;
}

/** Trang login/đăng ký: đã đăng nhập rồi → về trang chính. */
function GuestOnly({ children }: { children: ReactElement }) {
  const { state } = useAuth();
  if (state.status === 'loading') return <Loading />;
  return state.status === 'authenticated' ? <Navigate to="/" replace /> : children;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<RequireAuth><HomePage /></RequireAuth>} />
      <Route path="/admin" element={<Navigate to="/admin/ticket-types" replace />} />
      <Route path="/admin/events" element={<RequireAdmin><EventsPage /></RequireAdmin>} />
      <Route path="/admin/ticket-types" element={<RequireAdmin><TicketTypesPage /></RequireAdmin>} />
      <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
