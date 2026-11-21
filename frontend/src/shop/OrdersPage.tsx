import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { bookings, errorMessage, type Booking, type BookingStatus, type Slice } from '../api';
import { useAuth } from '../auth';
import { ShopLayout, vnd } from './ShopLayout';

const PAGE_SIZE = 10;

const STATUS: Record<BookingStatus, { label: string; tone: 'ok' | 'wait' | 'off' | 'warn' }> = {
  CONFIRMED: { label: 'Đã thanh toán', tone: 'ok' },
  AWAITING_PAYMENT: { label: 'Đang xác nhận thanh toán', tone: 'wait' },
  PENDING: { label: 'Đang giữ vé – chưa thanh toán', tone: 'wait' },
  REFUND_PENDING: { label: 'Đang hoàn tiền', tone: 'warn' },
  REFUNDED: { label: 'Đã hoàn tiền', tone: 'warn' },
  EXPIRED: { label: 'Hết thời gian giữ vé', tone: 'off' },
  CANCELLED: { label: 'Đã huỷ', tone: 'off' },
};

const when = (iso: string) =>
  new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso));

/** Lịch sử mua vé (A12): mọi đơn, mới trước, phân trang kiểu Slice (Trước / Sau – không đếm tổng). */
export function OrdersPage() {
  const { withToken } = useAuth();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Slice<Booking> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setData(null);
    withToken((t) => bookings.mine(t, page, PAGE_SIZE))
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(errorMessage(e)));
    return () => {
      alive = false;
    };
  }, [page]); // withToken đổi mỗi render → chỉ phụ thuộc page

  return (
    <ShopLayout>
      <h1 className="page">Đơn của tôi</h1>
      {error ? (
        <div className="box notice">{error}</div>
      ) : !data ? (
        <div className="box notice">Đang tải…</div>
      ) : data.items.length === 0 && page === 0 ? (
        <div className="box notice">
          <p>Bạn chưa có đơn nào.</p>
          <Link className="cta" to="/">Xem sự kiện</Link>
        </div>
      ) : (
        <>
          <ul className="order-list">
            {data.items.map((b) => {
              const st = STATUS[b.status];
              return (
                <li key={b.id}>
                  <Link to={b.status === 'PENDING' ? `/checkout/${b.id}` : `/orders/${b.id}`} className="order-row">
                    <div>
                      <strong>{b.event.title ?? 'Sự kiện'}</strong>
                      <small>{b.code} · {when(b.createdAt)} · {b.ticketCount} vé</small>
                    </div>
                    <div className="order-right">
                      <span className={`badge ${st.tone}`}>{st.label}</span>
                      <b>{vnd(b.total)}</b>
                      {b.status === 'CONFIRMED' && <small>Xem vé QR →</small>}
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
          <nav className="pager" aria-label="Phân trang">
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Trước</button>
            <span>Trang {page + 1}</span>
            <button type="button" disabled={!data.hasNext} onClick={() => setPage((p) => p + 1)}>Sau →</button>
          </nav>
        </>
      )}
    </ShopLayout>
  );
}
