import type { ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../auth';
import { setReturnTo } from '../returnTo';
import './shop.css';

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

/** Khung trang cho khách: header (logo, menu, tài khoản) + nội dung. Xem được khi chưa đăng nhập. */
export function ShopLayout({ children }: { children: ReactNode }) {
  const { state, logout } = useAuth();
  const here = useLocation().pathname;
  return (
    <div className="shop">
      <header className="shop-head">
        <div>
          <Link to="/" className="shop-brand">
            <span>
              <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 8a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4z" />
                <path d="M14 6v12" strokeDasharray="2 2" />
              </svg>
            </span>
            TicketHub
          </Link>
          <nav aria-label="Điều hướng chính" className="shop-nav">
            <NavLink to="/" end>Sự kiện</NavLink>
            {state.status === 'authenticated' && state.user.roles.includes('ADMIN') && <NavLink to="/admin">Quản trị</NavLink>}
            {state.status === 'authenticated' ? (
              <>
                <span className="avatar" title={state.user.email} aria-label={state.user.fullName || state.user.email}>
                  {initials(state.user.fullName || state.user.email)}
                </span>
                <button type="button" onClick={() => void logout()}>Đăng xuất</button>
              </>
            ) : state.status === 'anonymous' ? (
              <Link to="/login" onClick={() => setReturnTo(here)}>Đăng nhập</Link>
            ) : null}
          </nav>
        </div>
      </header>
      <main className="shop-main">{children}</main>
    </div>
  );
}

/** Khối ngày (THÁNG 12 / 20 / Thứ Bảy) theo giờ Việt Nam. */
export function DateBlock({ iso, large }: { iso: string | null; large?: boolean }) {
  if (!iso) {
    return <div className={`date-block tbd${large ? ' lg' : ''}`}><small>LỊCH</small><strong>Sắp có</strong></div>;
  }
  const d = new Date(iso);
  const part = (o: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat('vi-VN', { timeZone: VN_TZ, ...o }).format(d);
  return (
    <div className={`date-block${large ? ' lg' : ''}`}>
      <small>{part({ month: 'long' }).toUpperCase()}</small>
      <strong>{part({ day: 'numeric' })}</strong>
      {large && <small>{part({ weekday: 'long' })}</small>}
    </div>
  );
}

export const VN_TZ = 'Asia/Ho_Chi_Minh';
const timeFmt = new Intl.DateTimeFormat('vi-VN', { timeZone: VN_TZ, hour: '2-digit', minute: '2-digit' });
export const fmtTime = (iso: string | null) => (iso ? timeFmt.format(new Date(iso)) : null);
export const vnd = (n: number) => `${n.toLocaleString('vi-VN')} đ`;
export const EVENT_TYPE: Record<string, string> = { CONCERT: 'Ca nhạc', MOVIE: 'Phim', SPORT: 'Thể thao', THEATER: 'Sân khấu' };
