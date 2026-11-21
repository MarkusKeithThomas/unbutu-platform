import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { bookings, errorMessage, type Booking } from '../api';
import { useAuth } from '../auth';
import { saveCart } from './cart';
import { ShopLayout, vnd } from './ShopLayout';

/** Trình duyệt về từ cổng: kết quả do IPN quyết (server→server) → poll đơn mỗi 2 s, tối đa ~60 s (A11). */
const POLL_MS = 2000;
const MAX_POLLS = 30;

const FINAL = new Set(['CONFIRMED', 'REFUNDED', 'EXPIRED', 'CANCELLED', 'PENDING']);

export function OrderResultPage() {
  const { id = '' } = useParams();
  const { withToken } = useAuth();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [polls, setPolls] = useState(0);

  useEffect(() => {
    let alive = true;
    let timer: number | undefined;
    let n = 0;
    const tick = async () => {
      try {
        const b = await withToken((t) => bookings.get(t, id));
        if (!alive) return;
        setBooking(b);
        n += 1;
        setPolls(n);
        if (b.status === 'CONFIRMED') saveCart(b.event.id, {}); // mua xong → xoá lựa chọn
        if (FINAL.has(b.status) || n >= MAX_POLLS) return;
      } catch (e) {
        if (alive) setError(errorMessage(e));
        return;
      }
      timer = window.setTimeout(tick, POLL_MS);
    };
    void tick();
    return () => {
      alive = false;
      window.clearTimeout(timer);
    };
  }, [id]); // withToken là hàm mới mỗi lần render → chỉ phụ thuộc id

  if (error) {
    return <ShopLayout><div className="box notice"><p>{error}</p><Link to="/">← Về danh sách sự kiện</Link></div></ShopLayout>;
  }
  if (!booking) {
    return <ShopLayout><div className="box notice">Đang kiểm tra kết quả thanh toán…</div></ShopLayout>;
  }

  const s = booking.status;
  const done = s === 'CONFIRMED';
  return (
    <ShopLayout>
      <ol aria-label="Các bước" className="steps">
        <li className="done">1. Chọn vé</li>
        <li className={done ? 'done' : 'on'}>2. Thanh toán</li>
        <li className={done ? 'on' : ''} aria-current={done ? 'step' : undefined}>3. Hoàn tất</li>
      </ol>
      <div className="box result">
        {done ? (
          <>
            <div className="mark ok" aria-hidden="true">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#15803D" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12l5 5L20 7" /></svg>
            </div>
            <h1>Thanh toán thành công</h1>
            <p>Mã đơn <b>{booking.code}</b> · {booking.ticketCount} vé · {vnd(booking.total)}</p>
            <p className="sub">{booking.event.title}</p>
            <p className="sub">Vé điện tử sẽ được gửi tới email của bạn (đang hoàn thiện).</p>
            <Link className="cta" to="/">Mua thêm vé</Link>
          </>
        ) : s === 'AWAITING_PAYMENT' ? (
          <>
            <div className="spinner" aria-hidden="true" />
            <h1>Đang xác nhận thanh toán…</h1>
            <p>Mã đơn <b>{booking.code}</b>. Vui lòng không đóng trang.</p>
            {polls >= MAX_POLLS && (
              <p className="sub">Ngân hàng phản hồi chậm. Kết quả sẽ được cập nhật trong lịch sử đơn – bạn không bị trừ tiền 2 lần.</p>
            )}
          </>
        ) : s === 'PENDING' ? (
          <>
            <h1>Thanh toán chưa thành công</h1>
            <p>Bạn chưa bị trừ tiền. Vé vẫn đang được giữ cho đơn <b>{booking.code}</b>.</p>
            <Link className="cta" to={`/checkout/${booking.id}`}>Thanh toán lại</Link>
          </>
        ) : s === 'REFUND_PENDING' || s === 'REFUNDED' ? (
          <>
            <h1>Rất tiếc, vé đã hết</h1>
            <p>Khoản thanh toán cho đơn <b>{booking.code}</b> đến sau khi hết thời gian giữ vé và hạng vé đã bán hết.</p>
            <p className="sub">{s === 'REFUNDED' ? 'Đã hoàn tiền' : 'Đang hoàn tiền'} {vnd(booking.total)} về phương thức bạn đã dùng.</p>
            <Link className="cta" to={`/events/${booking.event.id}`}>Chọn vé khác</Link>
          </>
        ) : (
          <>
            <h1>{s === 'EXPIRED' ? 'Hết thời gian giữ vé' : 'Đơn hàng đã huỷ'}</h1>
            <p>Đơn <b>{booking.code}</b> không được thanh toán. Vé đã được trả lại.</p>
            <Link className="cta" to={`/events/${booking.event.id}`}>Chọn vé lại</Link>
          </>
        )}
      </div>
    </ShopLayout>
  );
}
