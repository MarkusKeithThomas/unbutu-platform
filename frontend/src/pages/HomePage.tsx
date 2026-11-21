import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { AuthLayout } from '../AuthLayout';

export function HomePage() {
  const { state, logout } = useAuth();
  const navigate = useNavigate();
  const via = (useLocation().state as { via?: string } | null)?.via;
  if (state.status !== 'authenticated') return null;
  const name = state.user.fullName || state.user.email;

  return (
    <AuthLayout>
      <div className="done">
        <div className="done-mark">
          <svg aria-hidden="true" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#15803D" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12l5 5L20 7" />
          </svg>
        </div>
        <h2 className="display">{via === 'register' ? 'Tạo tài khoản thành công' : 'Đăng nhập thành công'}</h2>
        <p>{via === 'google' ? 'Bạn đã đăng nhập bằng tài khoản Google.' : `Chào mừng, ${name}.`}</p>
        <p>{state.user.email}</p>
        {state.user.roles.includes('ADMIN') ? (
          <Link className="cta" to="/admin/ticket-types">Vào trang quản trị</Link>
        ) : (
          <a className="cta" aria-disabled="true" href="#" onClick={(e) => e.preventDefault()}>Trang mua vé – sắp có</a>
        )}
        <button type="button" className="btn-link" onClick={() => logout().then(() => navigate('/login', { replace: true }))}>
          Đăng xuất
        </button>
      </div>
    </AuthLayout>
  );
}
