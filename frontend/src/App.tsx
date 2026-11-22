import type { ReactElement } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom';
import { setReturnTo } from './returnTo';
import { EventsPage } from './admin/EventsPage';
import { TicketTypesPage } from './admin/TicketTypesPage';
import { LoadTestPage } from './admin/LoadTestPage';
import { useAuth } from './auth';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { CheckoutPage } from './shop/CheckoutPage';
import { OrderResultPage } from './shop/OrderResultPage';
import { OrdersPage } from './shop/OrdersPage';
import { EventPage } from './shop/EventPage';
import { EventsListPage } from './shop/EventsListPage';

function Loading() {
  return <div className="center">Đang tải…</div>;
}

/** Cần đăng nhập; chưa → /login rồi quay lại đúng trang này. */
function RequireAuth({ children }: { children: ReactElement }) {
  const { state } = useAuth();
  const here = useLocation().pathname;
  if (state.status === 'loading') return <Loading />;
  if (state.status !== 'authenticated') {
    setReturnTo(here);
    return <Navigate to="/login" replace />;
  }
  return children;
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

/** key theo id: sang sự kiện khác → mount lại (state + giỏ vé của sự kiện đó). */
function EventRoute() {
  const { id } = useParams();
  return <EventPage key={id} />;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<EventsListPage />} />
      <Route path="/events/:id" element={<EventRoute />} />
      <Route path="/checkout/:id" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
      <Route path="/orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
      <Route path="/orders/:id" element={<RequireAuth><OrderResultPage /></RequireAuth>} />
      <Route path="/admin" element={<Navigate to="/admin/ticket-types" replace />} />
      <Route path="/admin/events" element={<RequireAdmin><EventsPage /></RequireAdmin>} />
      <Route path="/admin/ticket-types" element={<RequireAdmin><TicketTypesPage /></RequireAdmin>} />
      <Route path="/admin/loadtest" element={<RequireAdmin><LoadTestPage /></RequireAdmin>} />
      <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
