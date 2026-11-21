import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { catalog, errorMessage, type EventSummary } from '../api';
import { DateBlock, EVENT_TYPE, ShopLayout, fmtTime, vnd } from './ShopLayout';

/** Trang chủ của khách: sự kiện sắp diễn ra đang mở bán (API công khai, CDN cache). "Xem thêm" tải trang kế. */
export function EventsListPage() {
  const [items, setItems] = useState<EventSummary[] | null>(null);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async (p: number) => {
    setLoading(true);
    try {
      const s = await catalog.browse(p);
      setItems((old) => (p === 0 ? s.items : [...(old ?? []), ...s.items]));
      setPage(p);
      setHasNext(s.hasNext);
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load(0);
  }, []);

  return (
    <ShopLayout>
      <h1 className="page">Sự kiện sắp diễn ra</h1>
      <p className="lead">Chọn sự kiện để xem hạng vé. Mỗi đơn tối đa 4 vé.</p>

      {error && (
        <div className="box notice">{error} <button type="button" className="btn-link" onClick={() => void load(page)}>Thử lại</button></div>
      )}
      {!error && items === null && <div className="box notice">Đang tải…</div>}
      {items !== null && items.length === 0 && <div className="box notice">Hiện chưa có sự kiện nào mở bán.</div>}

      {items && items.length > 0 && (
        <div className="ev-grid">
          {items.map((e) => (
            <Link key={e.id} to={`/events/${e.id}`} className="box ev-card">
              <DateBlock iso={e.startsAt} />
              <div>
                <span className="ev-type">{EVENT_TYPE[e.type] ?? e.type}</span>
                <h2>{e.title}</h2>
                <p className="meta">{[fmtTime(e.startsAt), e.venue].filter(Boolean).join(' · ') || 'Đang cập nhật'}</p>
                <p className="from">Từ <b>{vnd(e.priceFrom)}</b></p>
              </div>
            </Link>
          ))}
        </div>
      )}
      {hasNext && (
        <div className="more">
          <button type="button" className="btn btn-google" disabled={loading} onClick={() => void load(page + 1)}>
            {loading ? 'Đang tải…' : 'Xem thêm'}
          </button>
        </div>
      )}
    </ShopLayout>
  );
}
