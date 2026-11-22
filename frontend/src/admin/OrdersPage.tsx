import { useEffect, useRef, useState } from 'react';
import { adminOrders, catalog, errorMessage, type AdminBooking, type BookingStatus, type CatalogEvent } from '../api';
import { useAuth } from '../auth';
import { BOOKING_STATUS, BOOKING_STATUS_ORDER, money } from './labels';
import { OrderDetail } from './OrderDetail';
import { AdminLayout, SearchBox, fmtDateTime, fmtNumber } from './ui';

const PAGE = 20;

/** ADM-2: danh sách đơn – lọc trạng thái / sự kiện, tìm chính xác (mã đơn, email, SĐT, id), "Tải thêm" theo keyset. */
export function AdminOrdersPage() {
  const auth = useAuth();
  const tokenRef = useRef(auth.withToken);
  tokenRef.current = auth.withToken;
  const [status, setStatus] = useState<BookingStatus | ''>('');
  const [eventId, setEventId] = useState('');
  const [includeTest, setIncludeTest] = useState(false);
  const [input, setInput] = useState('');
  const [q, setQ] = useState('');
  const [items, setItems] = useState<AdminBooking[]>([]);
  const [next, setNext] = useState<string | null>(null);
  const [events, setEvents] = useState<CatalogEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const gen = useRef(0);   // bỏ kết quả của lần tải cũ khi bộ lọc đổi giữa chừng

  useEffect(() => {
    tokenRef.current((t) => catalog.events(t)).then(setEvents).catch(() => setEvents([]));
  }, []);

  // Gõ xong 400 ms mới tìm – tránh gọi API mỗi phím.
  useEffect(() => {
    const id = setTimeout(() => setQ(input.trim()), 400);
    return () => clearTimeout(id);
  }, [input]);

  const load = (cursor: string | null) => {
    const my = ++gen.current;
    setLoading(true);
    tokenRef.current((t) => adminOrders.list(t, { status: status || undefined, eventId: eventId || undefined, q: q || undefined, includeTest, cursor, size: PAGE }))
      .then((p) => {
        if (my !== gen.current) return;
        setItems((old) => (cursor ? [...old, ...p.items] : p.items));
        setNext(p.nextCursor);
        setError(null);
      })
      .catch((e) => { if (my === gen.current) setError(errorMessage(e)); })
      .finally(() => { if (my === gen.current) setLoading(false); });
  };

  useEffect(() => { load(null); }, [status, eventId, includeTest, q]);   // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <AdminLayout section="Đơn hàng" title="Đơn hàng">
      <div className="a-panel a-toolbar">
        <SearchBox id="order-q" label="Tìm đơn" value={input} onChange={setInput} />
        <label className="db-field db-event">Sự kiện
          <select className="a-input" value={eventId} onChange={(e) => setEventId(e.target.value)}>
            <option value="">Tất cả sự kiện</option>
            {events.filter((e) => includeTest || !e.hidden).map((e) => <option key={e.id} value={e.id}>{e.title}{e.hidden ? ' (ẩn)' : ''}</option>)}
          </select>
        </label>
        <label className="a-switch">
          <input type="checkbox" checked={includeTest} onChange={(e) => { setIncludeTest(e.target.checked); if (!e.target.checked) setEventId(''); }} />
          <span>Gồm load test</span>
        </label>
        <div role="group" aria-label="Lọc trạng thái" className="a-chips db-full">
          <button type="button" className="a-chip" aria-pressed={status === ''} onClick={() => setStatus('')}>Tất cả</button>
          {BOOKING_STATUS_ORDER.map((s) => (
            <button key={s} type="button" className="a-chip" aria-pressed={status === s} onClick={() => setStatus(s)}>{BOOKING_STATUS[s]}</button>
          ))}
        </div>
        <p className="lt-muted db-full">Tìm chính xác theo mã đơn (ORD-100123), email, số điện thoại người mua, hoặc id đơn / id tài khoản.</p>
      </div>

      {error && <p className="lt-err db-gap" role="alert">{error}</p>}

      <div className="a-panel db-gap">
        <div className="a-table-wrap">
          <table className="a-table">
            <thead>
              <tr><th>Mã đơn</th><th>Trạng thái</th><th>Sự kiện</th><th>Người mua</th><th className="num">Vé</th><th className="num">Tổng</th><th>Tạo lúc</th></tr>
            </thead>
            <tbody>
              {items.map((b) => (
                <tr key={b.id}>
                  <td><button type="button" className="a-link" onClick={() => setOpen(b.id)}>{b.code}</button></td>
                  <td><span className={`db-st ${b.status}`}>{BOOKING_STATUS[b.status]}</span></td>
                  <td>{b.event.title ?? '—'}{b.event.hidden && <span className="a-hidden-tag">Ẩn</span>}</td>
                  <td>{b.buyer.name ?? '—'}<br /><small className="small">{b.buyer.email ?? ''}</small></td>
                  <td className="num">{fmtNumber(b.ticketCount)}</td>
                  <td className="num">{money(b.total)}</td>
                  <td className="small">{fmtDateTime(b.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!loading && items.length === 0 && <div className="a-empty">Không có đơn nào khớp bộ lọc.</div>}
        <div className="a-pager">
          <span>Đang hiện <b>{fmtNumber(items.length)}</b> đơn, mới nhất trước</span>
          {next && <button type="button" className="a-btn sm" disabled={loading} onClick={() => load(next)}>{loading ? 'Đang tải…' : 'Tải thêm'}</button>}
          {loading && !next && <span>Đang tải…</span>}
        </div>
      </div>

      {open && <OrderDetail bookingId={open} onClose={() => setOpen(null)} />}
    </AdminLayout>
  );
}
