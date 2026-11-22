import { useEffect, useRef, useState } from 'react';
import { adminOrders, errorMessage, type AdminBookingDetail } from '../api';
import { useAuth } from '../auth';
import { BOOKING_STATUS, CANCEL_REASON, PAYMENT_STATUS, PAYMENT_TONE, money, vnpayLabel } from './labels';
import { SidePanel, fmtDateTime, fmtNumber } from './ui';

/** Chi tiết 1 đơn cho admin: người mua, dòng vé, mốc thời gian, mọi lần thanh toán (kèm đối soát gần nhất). */
export function OrderDetail({ bookingId, onClose }: { bookingId: string; onClose: () => void }) {
  const auth = useAuth();
  const tokenRef = useRef(auth.withToken);
  tokenRef.current = auth.withToken;
  const [d, setD] = useState<AdminBookingDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setD(null);
    setError(null);
    tokenRef.current((t) => adminOrders.detail(t, bookingId)).then(setD).catch((e) => setError(errorMessage(e)));
  }, [bookingId]);

  const b = d?.booking;
  return (
    <SidePanel title={b ? `Đơn ${b.code}` : 'Chi tiết đơn'} onClose={onClose}>
      {error && <p className="lt-err" role="alert">{error}</p>}
      {!d && !error && <p className="lt-muted">Đang tải…</p>}
      {b && d && (
        <div className="od">
          <div className="od-head">
            <span className={`db-st ${b.status}`}>{BOOKING_STATUS[b.status]}</span>
            {b.cancelReason && <span className="lt-muted">{CANCEL_REASON[b.cancelReason] ?? b.cancelReason}</span>}
            {b.event.hidden && <span className="a-hidden-tag">Load test</span>}
          </div>

          <dl className="od-grid">
            <dt>Sự kiện</dt><dd>{b.event.title ?? b.event.id}<br /><small className="lt-muted">{fmtDateTime(b.event.startsAt)}</small></dd>
            <dt>Người mua</dt><dd>{b.buyer.name ?? '—'}<br /><small className="lt-muted">{b.buyer.email ?? '—'} · {b.buyer.phone ?? '—'}</small></dd>
            <dt>Tài khoản</dt><dd><code>{b.userId}</code></dd>
            <dt>Tạo lúc</dt><dd>{fmtDateTime(b.createdAt)}</dd>
            <dt>Hạn giữ vé</dt><dd>{fmtDateTime(b.expiresAt)}</dd>
            <dt>Xác nhận lúc</dt><dd>{fmtDateTime(b.confirmedAt)}</dd>
            <dt>Cập nhật cuối</dt><dd>{fmtDateTime(b.updatedAt)}</dd>
            <dt>Phương thức</dt><dd>{b.paymentMethod ?? '—'}</dd>
          </dl>

          <h3 className="od-h3">Vé trong đơn</h3>
          <table className="a-table db-table">
            <thead><tr><th>Hạng vé</th><th className="num">SL</th><th className="num">Đơn giá</th><th className="num">Thành tiền</th></tr></thead>
            <tbody>
              {d.lines.map((l) => (
                <tr key={l.ticketTypeId}><td>{l.name}</td><td className="num">{fmtNumber(l.quantity)}</td><td className="num">{money(l.unitPrice)}</td><td className="num">{money(l.amount)}</td></tr>
              ))}
              <tr><td colSpan={3}>Phí</td><td className="num">{money(b.fee)}</td></tr>
              <tr className="od-total"><td colSpan={3}>Tổng</td><td className="num">{money(b.total)}</td></tr>
            </tbody>
          </table>

          <h3 className="od-h3">Thanh toán</h3>
          {d.paymentsUnavailable ? (
            <p className="lt-err">Tạm không tải được thanh toán (payment-service không phản hồi).</p>
          ) : !d.payments?.length ? (
            <p className="lt-muted">Chưa có lần thanh toán nào.</p>
          ) : (
            <ul className="od-pays">
              {d.payments.map((p) => (
                <li key={p.id}>
                  <div className="od-pay-head">
                    <span className={`db-st ${PAYMENT_TONE[p.status]}`}>{PAYMENT_STATUS[p.status]}</span>
                    {p.stuck && <span className="db-st CANCELLED">Treo</span>}
                    <b>Lần {p.attempt}</b><code>{p.txnRef}</code><span className="num">{money(p.amount)}</span>
                  </div>
                  <small className="lt-muted">
                    {p.method} · tạo {fmtDateTime(p.createdAt)} · trả {fmtDateTime(p.paidAt)}
                    {p.refundedAt && ` · hoàn ${fmtDateTime(p.refundedAt)}`} · mã cổng {vnpayLabel(p.providerResponseCode)}
                  </small>
                  {p.lastCheck && (
                    <small className="lt-muted">Đối soát {fmtDateTime(p.lastCheck.checkedAt)}: {p.lastCheck.action}{p.lastCheck.note ? ` – ${p.lastCheck.note}` : ''}</small>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </SidePanel>
  );
}
