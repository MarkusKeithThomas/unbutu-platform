import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError, bookings, catalog, errorMessage, inventory, type Availability, type CatalogEvent, type PublicTicketType } from '../api';
import { useAuth } from '../auth';
import { setReturnTo } from '../returnTo';
import { MAX_TICKETS_PER_ORDER, SERVICE_FEE_PER_TICKET, cartCount, loadCart, saveCart, type Cart } from './cart';
import { DateBlock, ShopLayout, fmtTime, vnd } from './ShopLayout';

const DOTS = ['#B45309', '#2563EB', '#0F766E', '#7C3AED', '#DB2777', '#6B7280'];
/** Poll tồn kho 5 s ± 1 s (jitter: hàng nghìn người mở trang cùng lúc không bắn đồng loạt). Tab ẩn → dừng. */
const POLL_MS = 5000;

/** Bước 1 – chọn vé. Bước giữ vé 10′ + thanh toán: PR sau (POST /api/bookings). */
export function EventPage() {
  const { id = '' } = useParams();
  const { state, withToken } = useAuth();
  const navigate = useNavigate();
  const [event, setEvent] = useState<CatalogEvent | null>(null);
  const [types, setTypes] = useState<PublicTicketType[] | null>(null);
  const [error, setError] = useState<{ msg: string; notFound: boolean } | null>(null);
  const [cart, setCart] = useState<Cart>(() => loadCart(id));
  const [toast, setToast] = useState<string | null>(null);
  const [stock, setStock] = useState<Record<string, Availability>>({});
  const [holding, setHolding] = useState(false);
  // 1 key cho 1 lựa chọn: bấm lại / mạng lỗi rồi thử lại → cùng key → server trả cùng đơn (không giữ vé 2 lần).
  // Đổi lựa chọn → key mới.
  const holdKey = useRef<{ cart: string; key: string } | null>(null);

  // Tồn kho realtime: lỗi inventory không chặn trang (vẫn chọn được theo tổng số vé), chỉ thiếu dòng "Còn x / y".
  useEffect(() => {
    let alive = true;
    let timer: number | undefined;
    const tick = async () => {
      if (document.visibilityState === 'visible') {
        try {
          const list = await inventory.availability(id);
          if (alive) setStock(Object.fromEntries(list.map((a) => [a.ticketTypeId, a])));
        } catch {
          /* giữ số cũ */
        }
      }
      if (alive) timer = window.setTimeout(tick, POLL_MS - 1000 + Math.random() * 2000);
    };
    void tick();
    return () => {
      alive = false;
      window.clearTimeout(timer);
    };
  }, [id]);

  useEffect(() => {
    let alive = true;
    Promise.all([catalog.event(id), catalog.eventTicketTypes(id)]).then(
      ([ev, tt]) => {
        if (!alive) return;
        setEvent(ev);
        setTypes(tt);
        // Bỏ khỏi giỏ hạng vé không còn bán được (bị tạm dừng / gỡ từ lần trước).
        setCart((old) => {
          const ok = new Set(tt.filter((t) => t.status === 'ON_SALE').map((t) => t.id));
          return Object.fromEntries(Object.entries(old).filter(([k]) => ok.has(k)));
        });
      },
      (e) => alive && setError({ msg: errorMessage(e), notFound: e instanceof ApiError && e.status === 404 }),
    );
    return () => {
      alive = false;
    };
  }, [id]);

  useEffect(() => saveCart(id, cart), [id, cart]);
  useEffect(() => {
    if (!toast) return;
    const t = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(t);
  }, [toast]);

  const count = cartCount(cart);
  const lines = useMemo(
    () => (types ?? []).filter((t) => cart[t.id]).map((t) => ({ t, qty: cart[t.id], amount: cart[t.id] * t.price })),
    [types, cart],
  );
  const subtotal = lines.reduce((a, l) => a + l.amount, 0);
  const fee = count * SERVICE_FEE_PER_TICKET;

  const change = (t: PublicTicketType, d: number) =>
    setCart((old) => {
      const cur = old[t.id] ?? 0;
      if (d > 0 && (cartCount(old) >= MAX_TICKETS_PER_ORDER || cur >= limitOf(t))) return old;
      const next = { ...old, [t.id]: Math.max(0, cur + d) };
      if (next[t.id] === 0) delete next[t.id];
      return next;
    });

  /** Số tối đa chọn được cho 1 hạng: theo tồn kho realtime nếu có, không thì theo tổng phát hành. */
  const limitOf = (t: PublicTicketType) => stock[t.id]?.available ?? t.totalQuantity;

  const proceed = () => {
    if (count === 0) return;
    if (state.status !== 'authenticated') {
      setReturnTo(`/events/${id}`); // giỏ vẫn còn trong sessionStorage
      navigate('/login');
      return;
    }
    const items = Object.entries(cart).map(([ticketTypeId, quantity]) => ({ ticketTypeId, quantity }));
    const sig = JSON.stringify(items);
    if (holdKey.current?.cart !== sig) holdKey.current = { cart: sig, key: crypto.randomUUID() };
    const key = holdKey.current.key;
    setHolding(true);
    withToken((t) => bookings.create(t, key, id, items))
      .then((b) => navigate(`/checkout/${b.id}`))
      .catch((e) => {
        if (e instanceof ApiError && (e.code === 'SOLD_OUT' || e.code === 'NOT_ON_SALE' || e.code === 'LIMIT_EXCEEDED')) {
          holdKey.current = null; // lựa chọn này đã bị từ chối hẳn → lần sau là yêu cầu mới
          void inventory.availability(id).then((l) => setStock(Object.fromEntries(l.map((a) => [a.ticketTypeId, a])))).catch(() => {});
        }
        setToast(errorMessage(e));
      })
      .finally(() => setHolding(false));
  };

  if (error) {
    return (
      <ShopLayout>
        <div className="box notice">
          <p>{error.notFound ? 'Không tìm thấy sự kiện này.' : error.msg}</p>
          <Link to="/">← Xem sự kiện khác</Link>
        </div>
      </ShopLayout>
    );
  }
  if (!event || !types) {
    return <ShopLayout><div className="box notice">Đang tải…</div></ShopLayout>;
  }

  return (
    <ShopLayout>
      <ol aria-label="Các bước" className="steps">
        <li className="on" aria-current="step">1. Chọn vé</li>
        <li>2. Thanh toán</li>
        <li>3. Hoàn tất</li>
      </ol>

      <div className="buy">
        <section>
          <div className="box ev-hero">
            <DateBlock iso={event.startsAt} large />
            <div>
              <h1>{event.title}</h1>
              <p>{[fmtTime(event.startsAt), event.venue].filter(Boolean).join(' · ') || 'Thời gian, địa điểm đang cập nhật'}</p>
              {event.description && <p>{event.description}</p>}
              <p className="fine">Mỗi đơn tối đa {MAX_TICKETS_PER_ORDER} vé · Vé được giữ 10 phút khi vào thanh toán</p>
            </div>
          </div>

          <div className="box" style={{ overflow: 'hidden' }}>
            <div className="tt-head">
              <h2>Chọn hạng vé</h2>
              {count < MAX_TICKETS_PER_ORDER && Object.keys(stock).length > 0 && <span>Tồn kho cập nhật realtime</span>}
              {count >= MAX_TICKETS_PER_ORDER && <span>Đã chọn tối đa {MAX_TICKETS_PER_ORDER} vé</span>}
            </div>
            {types.length === 0 && <div className="notice">Sự kiện chưa mở bán hạng vé nào.</div>}
            {types.map((t, i) => {
              const qty = cart[t.id] ?? 0;
              const s = stock[t.id];
              const soldOut = s ? s.state === 'SOLD_OUT' : t.totalQuantity === 0;
              const onSale = t.status === 'ON_SALE' && (s ? s.state !== 'PAUSED' : true) && !soldOut;
              const low = s?.state === 'LOW';
              const pct = s && s.total > 0 ? Math.round((s.available / s.total) * 100) : 100;
              return (
                <div key={t.id} className={`tt-row${qty > 0 ? ' sel' : ''}${onSale ? '' : ' off'}`}>
                  <div className="tt-name">
                    <i aria-hidden="true" style={{ background: DOTS[i % DOTS.length] }} />
                    <div>
                      <b>{t.name}</b>
                      {t.description && <small>{t.description}</small>}
                    </div>
                  </div>
                  <div className={`tt-stock${low ? ' low' : ''}${soldOut ? ' out' : ''}`}>
                    {s ? (soldOut ? 'Đã bán hết' : `Còn ${s.available.toLocaleString('vi-VN')} / ${s.total.toLocaleString('vi-VN')}`)
                      : `${t.totalQuantity.toLocaleString('vi-VN')} vé`}
                    {s && <div className="bar" aria-hidden="true"><i style={{ width: `${pct}%` }} /></div>}
                  </div>
                  <div className="tt-price">{vnd(t.price)}</div>
                  <div className="tt-act">
                    {onSale ? (
                      <div className="stepper">
                        <button type="button" aria-label={`Bớt 1 vé ${t.name}`} disabled={qty === 0} onClick={() => change(t, -1)}>−</button>
                        <span aria-live="polite">{qty}</span>
                        <button type="button" aria-label={`Thêm 1 vé ${t.name}`} disabled={count >= MAX_TICKETS_PER_ORDER || qty >= limitOf(t)}
                          onClick={() => change(t, 1)}>+</button>
                      </div>
                    ) : (
                      <span className="off">{soldOut ? 'Hết vé' : 'Tạm dừng bán'}</span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        <aside aria-label="Tóm tắt đơn hàng" className="box summary">
          <h2>Đơn hàng</h2>
          {count === 0 && <p>Chưa chọn vé nào. Dùng nút + để thêm vé.</p>}
          {lines.map((l) => (
            <div key={l.t.id} className="line"><span>{l.t.name} × {l.qty}</span><b>{vnd(l.amount)}</b></div>
          ))}
          <div className="sub">
            <div><span>Tạm tính</span><span>{vnd(subtotal)}</span></div>
            <div><span>Phí dịch vụ</span><span>{vnd(fee)}</span></div>
          </div>
          <div className="total"><span>Tổng cộng</span><strong>{vnd(subtotal + fee)}</strong></div>
          <button type="button" className="btn btn-primary" disabled={count === 0 || holding} onClick={proceed}>
            {holding ? 'Đang giữ vé…' : state.status === 'authenticated' || count === 0 ? 'Tiếp tục thanh toán' : 'Đăng nhập để tiếp tục'}
          </button>
          <p className="hint">Giá và phí chính thức được xác nhận ở bước thanh toán.</p>
        </aside>
      </div>
      {toast && <div role="status" className="shop-toast">{toast}</div>}
    </ShopLayout>
  );
}
