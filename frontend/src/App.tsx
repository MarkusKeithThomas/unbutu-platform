import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
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
      <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
