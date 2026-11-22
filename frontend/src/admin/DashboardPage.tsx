import { useEffect, useRef, useState } from 'react';
import { adminStats, catalog, errorMessage, type AdminStats, type CatalogEvent } from '../api';
import { useAuth } from '../auth';
import { LineChart } from './LineChart';
import { BOOKING_STATUS as STATUS, BOOKING_STATUS_ORDER as STATUS_ORDER, CANCEL_REASON as REASON, money } from './labels';
import { AdminLayout, fmtDateTime, fmtNumber, fmtStamp } from './ui';

const SERIES_1 = '#2a78d6';   // dataviz palette – slot 1 (blue)
const SERIES_2 = '#1baf7a';   // slot 2 (green)
const REFRESH_MS = 60_000;    // = cache phía server (60 s) – làm mới nhanh hơn cũng không có số mới

const PRESETS = [
  { key: 'today', label: 'Hôm nay', days: 1 },
  { key: '7d', label: '7 ngày', days: 7 },
  { key: '30d', label: '30 ngày', days: 30 },
  { key: '90d', label: '90 ngày', days: 90 },
] as const;

/** YYYY-MM-DD theo giờ VN (server cũng gom ngày theo Asia/Ho_Chi_Minh). */
const vnDate = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(d);
const daysAgo = (n: number) => vnDate(new Date(Date.now() - n * 86_400_000));
const pct = (r: number) => `${(r * 100).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%`;
const dm = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

type Filter = { from: string; to: string; eventId: string; includeTest: boolean; preset: string };

export function DashboardPage() {
  const auth = useAuth();
  const tokenRef = useRef(auth.withToken);
  tokenRef.current = auth.withToken;
  const [filter, setFilter] = useState<Filter>({ from: daysAgo(29), to: daysAgo(0), eventId: '', includeTest: false, preset: '30d' });
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [events, setEvents] = useState<CatalogEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    tokenRef.current((t) => catalog.events(t)).then(setEvents).catch(() => setEvents([]));
  }, []);

  useEffect(() => {
    let alive = true;
    const load = () => {
      setLoading(true);
      tokenRef.current((t) => adminStats.get(t, { from: filter.from, to: filter.to, eventId: filter.eventId || undefined, includeTest: filter.includeTest }))
        .then((s) => { if (alive) { setStats(s); setError(null); } })
        .catch((e) => { if (alive) setError(errorMessage(e)); })
        .finally(() => { if (alive) setLoading(false); });
    };
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => { alive = false; clearInterval(timer); };
  }, [filter.from, filter.to, filter.eventId, filter.includeTest]);

  const pickPreset = (key: string, days: number) =>
    setFilter((f) => ({ ...f, preset: key, from: daysAgo(days - 1), to: daysAgo(0) }));
  const setRange = (k: 'from' | 'to', v: string) => v && setFilter((f) => ({ ...f, [k]: v, preset: '' }));

  const visibleEvents = events.filter((e) => filter.includeTest || !e.hidden);
  const k = stats?.kpi;
  const daily = stats?.daily ?? [];
  const x = daily.map((_, i) => i);
  const fmtDay = (i: number) => (daily[i] ? dm(daily[i].date) : '');
  const reasons = stats ? Object.entries(stats.cancelReasons) : [];

  return (
    <AdminLayout section="Tổng quan" title="Tổng quan bán vé"
      action={stats && <span className="lt-muted">Cập nhật {fmtStamp(stats.generatedAt)} · tự làm mới mỗi phút</span>}>
      <div className="a-panel a-toolbar db-filters">
        <div role="group" aria-label="Khoảng thời gian" className="a-chips">
          {PRESETS.map((p) => (
            <button key={p.key} type="button" className="a-chip" aria-pressed={filter.preset === p.key} onClick={() => pickPreset(p.key, p.days)}>
              {p.label}
            </button>
          ))}
        </div>
        <label className="db-field">Từ
          <input type="date" className="a-input" value={filter.from} max={filter.to} onChange={(e) => setRange('from', e.target.value)} />
        </label>
        <label className="db-field">Đến
          <input type="date" className="a-input" value={filter.to} min={filter.from} max={daysAgo(0)} onChange={(e) => setRange('to', e.target.value)} />
        </label>
        <label className="db-field db-event">Sự kiện
          <select className="a-input" value={filter.eventId} onChange={(e) => setFilter((f) => ({ ...f, eventId: e.target.value }))}>
            <option value="">Tất cả sự kiện</option>
            {visibleEvents.map((e) => <option key={e.id} value={e.id}>{e.title}{e.hidden ? ' (ẩn)' : ''}</option>)}
          </select>
        </label>
        <label className="a-switch">
          <input type="checkbox" checked={filter.includeTest}
            onChange={(e) => setFilter((f) => ({ ...f, includeTest: e.target.checked, eventId: e.target.checked ? f.eventId : '' }))} />
          <span>Gồm dữ liệu load test<br /><small className="lt-muted">sự kiện ẩn khỏi khách</small></span>
        </label>
      </div>

      {error && <p className="lt-err db-gap" role="alert">Không tải được số liệu: {error}</p>}
      {!stats && loading && <p className="lt-muted db-gap">Đang tải số liệu…</p>}

      {stats && k && (
        <>
          <div className="lt-tiles db-gap" aria-busy={loading}>
            <div className="lt-tile hero">
              <small>Doanh thu thuần</small>
              <strong>{money(k.netRevenue)}</strong>
              <span>đơn thành công · đã nhận {money(k.grossRevenue)}</span>
            </div>
            <div className="lt-tile"><small>Vé đã bán</small><strong>{fmtNumber(k.ticketsSold)}</strong><span>{fmtNumber(k.confirmedOrders)} đơn thành công</span></div>
            <div className="lt-tile"><small>Đang chờ</small><strong>{fmtNumber(k.openOrders)}</strong><span>giữ vé + chờ thanh toán</span></div>
            <div className="lt-tile"><small>Lỗi / hết hạn</small><strong>{fmtNumber(k.failedOrders)}</strong><span>đơn huỷ hoặc quá hạn giữ</span></div>
            <div className="lt-tile"><small>Tỷ lệ chốt đơn</small><strong>{pct(k.conversionRate)}</strong><span>thành công / đơn đã có kết quả</span></div>
            <div className="lt-tile"><small>Hoàn tiền</small><strong>{money(k.refundAmount)}</strong><span>{fmtNumber(k.refundOrders)} đơn</span></div>
            <div className="lt-tile"><small>Giá trị TB / đơn</small><strong>{money(k.avgOrderValue)}</strong><span>trên {fmtNumber(k.totalOrders)} đơn tạo</span></div>
          </div>

          <div className="a-panel lt-pad db-gap lt-charts">
            <LineChart title="Doanh thu thuần theo ngày" x={x} xFormat={fmtDay}
              series={[{ name: 'Doanh thu', values: daily.map((d) => d.netRevenue / 1_000_000), color: SERIES_1, unit: 'triệu đ' }]} />
            <LineChart title="Đơn hàng theo ngày" x={x} xFormat={fmtDay}
              series={[
                { name: 'Đơn tạo', values: daily.map((d) => d.orders), color: SERIES_1, unit: 'đơn' },
                { name: 'Thành công', values: daily.map((d) => d.confirmedOrders), color: SERIES_2, unit: 'đơn' },
              ]} />
          </div>

          <div className="db-grid db-gap">
            <section className="a-panel" aria-labelledby="db-status">
              <h2 id="db-status" className="lt-h2 lt-pad">Đơn theo trạng thái</h2>
              <div className="a-table-wrap">
                <table className="a-table db-table">
                  <thead><tr><th>Trạng thái</th><th className="num">Đơn</th><th className="num">Vé</th><th className="num">Số tiền</th></tr></thead>
                  <tbody>
                    {STATUS_ORDER.map((s) => {
                      const r = stats.byStatus[s];
                      return (
                        <tr key={s}>
                          <td><span className={`db-st ${s}`}>{STATUS[s]}</span></td>
                          <td className="num">{fmtNumber(r?.orders ?? 0)}</td>
                          <td className="num">{fmtNumber(r?.tickets ?? 0)}</td>
                          <td className="num">{money(r?.amount ?? 0)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </section>

            <section className="a-panel" aria-labelledby="db-reasons">
              <h2 id="db-reasons" className="lt-h2 lt-pad">Vì sao đơn không thành</h2>
              {reasons.length === 0 ? (
                <div className="a-empty">Không có đơn huỷ / hết hạn trong khoảng này.</div>
              ) : (
                <div className="a-table-wrap">
                  <table className="a-table db-table">
                    <thead><tr><th>Lý do</th><th className="num">Đơn</th><th className="num">Tỷ lệ</th></tr></thead>
                    <tbody>
                      {reasons.map(([r, n]) => (
                        <tr key={r}>
                          <td>{REASON[r] ?? r}</td>
                          <td className="num">{fmtNumber(n)}</td>
                          <td className="num">{pct(k.failedOrders ? n / k.failedOrders : 0)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </div>

          <section className="a-panel db-gap" aria-labelledby="db-top">
            <h2 id="db-top" className="lt-h2 lt-pad">Sự kiện bán chạy</h2>
            {stats.topEvents.length === 0 ? (
              <div className="a-empty">Chưa có đơn thành công trong khoảng này.</div>
            ) : (
              <div className="a-table-wrap">
                <table className="a-table">
                  <thead><tr><th>Sự kiện</th><th>Diễn ra</th><th className="num">Đơn</th><th className="num">Vé</th><th className="num">Doanh thu</th></tr></thead>
                  <tbody>
                    {stats.topEvents.map((e) => (
                      <tr key={e.eventId}>
                        <td>{e.title ?? e.eventId}{e.hidden && <span className="a-hidden-tag">Ẩn</span>}</td>
                        <td className="small">{fmtDateTime(e.startsAt)}</td>
                        <td className="num">{fmtNumber(e.confirmedOrders)}</td>
                        <td className="num">{fmtNumber(e.ticketsSold)}</td>
                        <td className="num">{money(e.netRevenue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </AdminLayout>
  );
}
