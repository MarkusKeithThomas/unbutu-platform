import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import './admin.css';

const TicketMark = () => (
  <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#111827" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 8a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4z" />
  </svg>
);

/** Khung trang quản trị: menu trái + nội dung. */
export function AdminLayout({ section, title, action, children }: { section: string; title: string; action?: ReactNode; children: ReactNode }) {
  const { state, logout } = useAuth();
  const navigate = useNavigate();
  const name = state.status === 'authenticated' ? state.user.fullName || state.user.email : '';
  return (
    <div className="admin">
      <nav aria-label="Menu quản trị" className="admin-nav">
        <div className="admin-brand">
          <div className="brand-mark"><TicketMark /></div>
          <div>
            <strong>TicketHub</strong>
            <small>Quản trị</small>
          </div>
        </div>
        <ul>
          <li><NavLink to="/admin/events">Sự kiện</NavLink></li>
          <li><NavLink to="/admin/ticket-types">Hạng vé</NavLink></li>
          <li><NavLink to="/admin/loadtest">Load test</NavLink></li>
          <li><NavLink to="/" end>Về trang chủ</NavLink></li>
        </ul>
        <div className="admin-me">
          <strong>{name} · Admin</strong>
          <button type="button" onClick={() => logout().then(() => navigate('/login', { replace: true }))}>Đăng xuất</button>
        </div>
      </nav>
      <main className="admin-main">
        <div className="admin-head">
          <div>
            <p>Quản trị / {section}</p>
            <h1>{title}</h1>
          </div>
          {action}
        </div>
        {children}
      </main>
    </div>
  );
}

export const PlusIcon = () => (
  <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" strokeWidth="2.2" strokeLinecap="round"><path d="M12 5v14M5 12h14" /></svg>
);

export function SearchBox({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="a-search">
      <label htmlFor={id} className="sr-only">{label}</label>
      <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#6B7280" strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg>
      <input id={id} type="search" placeholder={`${label}…`} value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

/** Phân trang phía client. */
export function usePaged<T>(items: T[], perPage: number) {
  const [page, setPage] = useState(0);
  const pages = Math.max(1, Math.ceil(items.length / perPage));
  const p = Math.min(page, pages - 1);
  return { slice: items.slice(p * perPage, p * perPage + perPage), page: p, pages, setPage, from: items.length ? p * perPage + 1 : 0 };
}

export function Pager({ total, noun, paged }: { total: number; noun: string; paged: ReturnType<typeof usePaged<unknown>> }) {
  const { page, pages, setPage, from, slice } = paged;
  return (
    <div className="a-pager">
      <span>Hiển thị {from}–{from ? from + slice.length - 1 : 0} trong {total} {noun}</span>
      <div>
        <button type="button" className="a-btn sm" disabled={page === 0} onClick={() => setPage(page - 1)}>← Trước</button>
        <b>Trang {page + 1} / {pages}</b>
        <button type="button" className="a-btn sm" disabled={page >= pages - 1} onClick={() => setPage(page + 1)}>Sau →</button>
      </div>
    </div>
  );
}

/** Đóng bằng Esc. */
function useEscape(onClose: () => void) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onClose]);
}

export function Drawer(props: { title: string; saveLabel: string; busy: boolean; onClose: () => void; onSave: () => void; children: ReactNode }) {
  useEscape(props.onClose);
  return (
    <div className="a-overlay" onMouseDown={(e) => e.target === e.currentTarget && props.onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="dr-title" className="a-drawer">
        <header>
          <h2 id="dr-title">{props.title}</h2>
          <button type="button" className="a-close" onClick={props.onClose} aria-label="Đóng">
            <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#374151" strokeWidth="2" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </header>
        <form className="a-drawer-body" id="dr-form" noValidate onSubmit={(e) => { e.preventDefault(); props.onSave(); }}>
          {props.children}
        </form>
        <footer>
          <button type="button" className="a-btn" onClick={props.onClose}>Huỷ</button>
          <button type="submit" form="dr-form" className="a-btn primary" disabled={props.busy}>{props.busy ? 'Đang lưu…' : props.saveLabel}</button>
        </footer>
      </section>
    </div>
  );
}

export function Confirm(props: { title: string; message: string; confirmLabel?: string; busy: boolean; onCancel: () => void; onConfirm?: () => void }) {
  useEscape(props.onCancel);
  return (
    <div className="a-overlay center">
      <section role="alertdialog" aria-modal="true" aria-labelledby="cf-title" className="a-confirm">
        <h2 id="cf-title">{props.title}</h2>
        <p>{props.message}</p>
        <div>
          <button type="button" className="a-btn" onClick={props.onCancel}>{props.onConfirm ? 'Huỷ' : 'Đóng'}</button>
          {props.onConfirm && (
            <button type="button" className="a-btn danger" disabled={props.busy} onClick={props.onConfirm}>{props.confirmLabel}</button>
          )}
        </div>
      </section>
    </div>
  );
}

/** Thông báo ngắn ở đáy màn hình. */
export function useToast() {
  const [toast, setToast] = useState<{ msg: string; error: boolean } | null>(null);
  const timer = useRef<number>();
  const show = useCallback((msg: string, error = false) => {
    window.clearTimeout(timer.current);
    setToast({ msg, error });
    timer.current = window.setTimeout(() => setToast(null), error ? 4000 : 2200);
  }, []);
  useEffect(() => () => window.clearTimeout(timer.current), []);
  const node = toast && <div role="status" className={`a-toast${toast.error ? ' error' : ''}`}>{toast.msg}</div>;
  return { show, node };
}

const dateTime = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const shortStamp = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
export const fmtDateTime = (iso: string | null) => (iso ? dateTime.format(new Date(iso)) : '—');
export const fmtStamp = (iso: string) => shortStamp.format(new Date(iso));
export const fmtNumber = (n: number) => n.toLocaleString('vi-VN');
