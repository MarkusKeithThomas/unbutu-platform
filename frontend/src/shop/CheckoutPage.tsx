import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { bookings, errorMessage, type Booking } from '../api';
import { useAuth } from '../auth';
import { DateBlock, ShopLayout, fmtTime, vnd } from './ShopLayout';

const METHODS = [
  { id: 'VNBANK', name: 'Thẻ ATM nội địa (NAPAS)', desc: 'Chuyển sang trang ngân hàng để xác thực OTP' },
  { id: 'VNPAYQR', name: 'QR chuyển khoản', desc: 'Quét mã bằng app ngân hàng bất kỳ' },
  { id: 'INTCARD', name: 'Thẻ quốc tế', desc: 'Visa, Mastercard, JCB — xác thực 3-D Secure' },
] as const;

/** Poll trạng thái đơn (đơn có thể hết hạn / bị huỷ ở tab khác). Đồng hồ đếm ngược chạy local mỗi giây. */
const POLL_MS = 10_000;

function useCountdown(expiresAt: string | undefined) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, []);
  const left = expiresAt ? Math.max(0, Math.floor((new Date(expiresAt).getTime() - now) / 1000)) : 0;
  return { left, label: `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}` };
}

/** Bước 2 – vé đang được giữ 10′: thông tin người mua + phương thức. Nút "Xác nhận thanh toán": PAY-1. */
export function CheckoutPage() {
  const { id = '' } = useParams();
  const { state, withToken } = useAuth();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [method, setMethod] = useState<string>('VNBANK');
  const user = state.status === 'authenticated' ? state.user : null;
  const [buyer, setBuyer] = useState({ name: user?.fullName ?? '', email: user?.email ?? '', phone: user?.phone ?? '' });

  const load = useCallback(() => withToken((t) => bookings.get(t, id)).then(setBooking, (e) => setError(errorMessage(e))),
    [id]); // withToken là hàm mới mỗi lần render → chỉ phụ thuộc id
  useEffect(() => {
    void load();
    const t = window.setInterval(() => document.visibilityState === 'visible' && void load(), POLL_MS);
    return () => window.clearInterval(t);
  }, [load]);

  const { left, label } = useCountdown(booking?.expiresAt);
  const holding = booking?.status === 'PENDING' && left > 0;

  const backToTickets = async () => {
    if (!booking) return;
    setBusy(true);
    try {
      if (booking.status === 'PENDING') await withToken((t) => bookings.cancel(t, booking.id)); // nhả vé ngay cho người khác
    } catch {
      /* huỷ lỗi: vé tự trả khi hết hạn */
    }
    navigate(`/events/${booking.event.id}`); // giỏ vẫn còn trong sessionStorage → chọn lại nhanh
  };

  if (error) {
    return <ShopLayout><div className="box notice"><p>{error}</p><Link to="/">← Về danh sách sự kiện</Link></div></ShopLayout>;
  }
  if (!booking) {
    return <ShopLayout><div className="box notice">Đang tải đơn hàng…</div></ShopLayout>;
  }

  return (
    <ShopLayout>
      <ol aria-label="Các bước" className="steps">
        <li className="done">1. Chọn vé</li>
        <li className="on" aria-current="step">2. Thanh toán</li>
        <li>3. Hoàn tất</li>
      </ol>

      <div className="buy">
        <section>
          <div className="box ev-hero">
            <DateBlock iso={booking.event.startsAt} large />
            <div>
              <h1>{booking.event.title ?? 'Sự kiện'}</h1>
              <p>{[fmtTime(booking.event.startsAt), booking.event.venue].filter(Boolean).join(' · ')}</p>
              <p className="fine">Mã đơn <b>{booking.code}</b></p>
            </div>
          </div>

          {holding ? (
            <div className="box pay">
              <div className="pay-head">
                <h2>Thông tin thanh toán</h2>
                <span className={`hold${left < 60 ? ' urgent' : ''}`} role="timer" aria-live="off">Vé đang được giữ · còn {label}</span>
              </div>
              <div className="pay-grid">
                <div className="field span2">
                  <label htmlFor="f-name">Họ và tên</label>
                  <input id="f-name" className="input" value={buyer.name} onChange={(e) => setBuyer({ ...buyer, name: e.target.value })}
                    placeholder="Nguyễn Văn A" autoComplete="name" maxLength={100} />
                </div>
                <div className="field">
                  <label htmlFor="f-email">Email nhận vé</label>
                  <input id="f-email" className="input" type="email" value={buyer.email} onChange={(e) => setBuyer({ ...buyer, email: e.target.value })}
                    placeholder="ban@email.com" autoComplete="email" />
                </div>
                <div className="field">
                  <label htmlFor="f-phone">Số điện thoại</label>
                  <input id="f-phone" className="input" type="tel" value={buyer.phone} onChange={(e) => setBuyer({ ...buyer, phone: e.target.value })}
                    placeholder="09xx xxx xxx" autoComplete="tel" maxLength={20} />
                </div>
              </div>
              <fieldset className="methods">
                <legend>Phương thức thanh toán</legend>
                {METHODS.map((m) => (
                  <label key={m.id} className={`method${method === m.id ? ' on' : ''}`}>
                    <input type="radio" name="pay" checked={method === m.id} onChange={() => setMethod(m.id)} />
                    <span><b>{m.name}</b><small>{m.desc}</small></span>
                  </label>
                ))}
              </fieldset>
              <button type="button" className="a-back" disabled={busy} onClick={() => void backToTickets()}>← Chọn lại vé</button>
            </div>
          ) : (
            <div className="box notice">
              <p>
                {booking.status === 'CANCELLED' ? 'Đơn hàng đã được huỷ, vé đã trả lại.'
                  : booking.status === 'CONFIRMED' ? 'Đơn hàng đã thanh toán.'
                  : 'Hết thời gian giữ vé – vé đã được trả lại cho người khác.'}
              </p>
              <button type="button" className="btn btn-primary" style={{ padding: '0 20px' }} onClick={() => navigate(`/events/${booking.event.id}`)}>
                Chọn vé lại
              </button>
            </div>
          )}
        </section>

        <aside aria-label="Tóm tắt đơn hàng" className="box summary">
          <h2>Đơn hàng</h2>
          {booking.lines.map((l) => (
            <div key={l.ticketTypeId} className="line"><span>{l.name} × {l.quantity}</span><b>{vnd(l.amount)}</b></div>
          ))}
          <div className="sub">
            <div><span>Tạm tính</span><span>{vnd(booking.subtotal)}</span></div>
            <div><span>Phí dịch vụ</span><span>{vnd(booking.fee)}</span></div>
          </div>
          <div className="total"><span>Tổng cộng</span><strong>{vnd(booking.total)}</strong></div>
          {holding && (
            <>
              <button type="button" className="btn btn-primary" disabled title="Có ở bản cập nhật tới">
                Xác nhận thanh toán {vnd(booking.total)}
              </button>
              <p className="hint">Cổng thanh toán đang được tích hợp – sẽ có ở bản cập nhật tới. Vé vẫn được giữ tới khi hết giờ.</p>
            </>
          )}
        </aside>
      </div>
    </ShopLayout>
  );
}
