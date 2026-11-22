import { useEffect, useRef, useState } from 'react';
import { adminOrders, errorMessage, type AdminPayment, type PaymentStatus, type PaymentSummary } from '../api';
import { useAuth } from '../auth';
import { PAYMENT_STATUS, PAYMENT_TONE, money, vnpayLabel } from './labels';
import { OrderDetail } from './OrderDetail';
import { AdminLayout, SearchBox, fmtDateTime, fmtNumber, fmtStamp } from './ui';

const PAGE = 20;
type Tab = 'FAILED' | 'STUCK' | 'PENDING' | 'SUCCEEDED' | 'REFUNDED' | '';
const TABS: { key: Tab; label: string }[] = [
  { key: 'FAILED', label: 'Thất bại' },
  { key: 'STUCK', label: 'Treo > 15′' },
  { key: 'PENDING', label: 'Đang chờ' },
  { key: 'SUCCEEDED', label: 'Thành công' },
  { key: 'REFUNDED', label: 'Đã hoàn' },
  { key: '', label: 'Tất cả' },
];

const vnDate = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(d);

/** ADM-3: thanh toán lỗi / treo – tổng hợp 30 ngày + danh sách theo tab, mã lỗi cổng, lần đối soát gần nhất. */
export function PaymentsPage() {
  const auth = useAuth();
  const tokenRef = useRef(auth.withToken);
  tokenRef.current = auth.withToken;
  const [tab, setTab] = useState<Tab>('FAILED');
  const [input, setInput] = useState('');
  const [q, setQ] = useState('');
  const [summary, setSummary] = useState<PaymentSummary | null>(null);
  const [items, setItems] = useState<AdminPayment[]>([]);
  const [next, setNext] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const gen = useRef(0);

  useEffect(() => {
    const to = vnDate(new Date());
    const from = vnDate(new Date(Date.now() - 29 * 86_400_000));
    tokenRef.current((t) => adminOrders.paymentSummary(t, from, to)).then(setSummary).catch((e) => setError(errorMessage(e)));
  }, []);

  useEffect(() => {
    const id = setTimeout(() => setQ(input.trim()), 400);
    return () => clearTimeout(id);
  }, [input]);

  const load = (cursor: string | null) => {
    const my = ++gen.current;
    setLoading(true);
    const filter = tab === 'STUCK' ? { stuck: true } : { status: tab || undefined };
    tokenRef.current((t) => adminOrders.payments(t, { ...filter, q: q || undefined, cursor, size: PAGE }))
      .then((p) => {
        if (my !== gen.current) return;
        setItems((old) => (cursor ? [...old, ...p.items] : p.items));
        setNext(p.nextCursor);
        setError(null);
      })
      .catch((e) => { if (my === gen.current) setError(errorMessage(e)); })
      .finally(() => { if (my === gen.current) setLoading(false); });
  };

  useEffect(() => { load(null); }, [tab, q]);   // eslint-disable-line react-hooks/exhaustive-deps

  const c = summary?.countByStatus ?? {};
  const a = summary?.amountByStatus ?? {};
  const total = Object.values(c).reduce((s, n) => s + (n ?? 0), 0);
  const failRate = total ? (c.FAILED ?? 0) / total : 0;
  const codes = summary ? Object.entries(summary.failureCodes) : [];

  return (
    <AdminLayout section="Thanh toán" title="Thanh toán"
      action={summary && <span className="lt-muted">30 ngày gần nhất · cập nhật {fmtStamp(summary.generatedAt)}</span>}>
      {summary && (
        <div className="lt-tiles">
          <div className={`lt-tile${summary.stuck ? ' warn' : ''}`}><small>Treo &gt; 15 phút</small><strong>{fmtNumber(summary.stuck)}</strong><span>PENDING mà đối soát chưa chốt được</span></div>
          <div className="lt-tile"><small>Thất bại</small><strong>{fmtNumber(c.FAILED ?? 0)}</strong><span>{(failRate * 100).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}% số lần thanh toán</span></div>
          <div className="lt-tile"><small>Thành công</small><strong>{fmtNumber(c.SUCCEEDED ?? 0)}</strong><span>{money(a.SUCCEEDED ?? 0)}</span></div>
          <div className="lt-tile"><small>Đã hoàn</small><strong>{fmtNumber(c.REFUNDED ?? 0)}</strong><span>{money(a.REFUNDED ?? 0)}</span></div>
          <div className="lt-tile"><small>Đang chờ</small><strong>{fmtNumber(c.PENDING ?? 0)}</strong><span>khách đang ở cổng thanh toán</span></div>
        </div>
      )}

      {codes.length > 0 && (
        <section className="a-panel db-gap" aria-labelledby="pay-codes">
          <h2 id="pay-codes" className="lt-h2 lt-pad">Lý do thất bại (mã cổng thanh toán)</h2>
          <div className="a-table-wrap">
            <table className="a-table db-table">
              <thead><tr><th>Mã</th><th className="num">Số lần</th><th className="num">Tỷ lệ</th></tr></thead>
              <tbody>
                {codes.map(([code, n]) => (
                  <tr key={code}><td>{vnpayLabel(code)}</td><td className="num">{fmtNumber(n)}</td>
                    <td className="num">{(((n / (c.FAILED || 1)) * 100)).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <div className="a-panel a-toolbar db-gap">
        <div role="group" aria-label="Lọc thanh toán" className="a-chips">
          {TABS.map((t) => (
            <button key={t.key || 'all'} type="button" className="a-chip" aria-pressed={tab === t.key} onClick={() => setTab(t.key)}>{t.label}</button>
          ))}
        </div>
        <SearchBox id="pay-q" label="Tìm theo mã đơn hoặc mã giao dịch" value={input} onChange={setInput} />
      </div>

      {error && <p className="lt-err db-gap" role="alert">{error}</p>}

      <div className="a-panel db-gap">
        <div className="a-table-wrap">
          <table className="a-table">
            <thead>
              <tr><th>Mã giao dịch</th><th>Đơn</th><th>Trạng thái</th><th className="num">Số tiền</th><th>Mã cổng</th><th>Đối soát gần nhất</th><th>Tạo lúc</th></tr>
            </thead>
            <tbody>
              {items.map((p) => (
                <tr key={p.id}>
                  <td><code>{p.txnRef}</code><br /><small className="small">{p.method} · lần {p.attempt}</small></td>
                  <td><button type="button" className="a-link" onClick={() => setOpen(p.bookingId)}>{p.bookingCode}</button></td>
                  <td>
                    <span className={`db-st ${PAYMENT_TONE[p.status as PaymentStatus]}`}>{PAYMENT_STATUS[p.status as PaymentStatus]}</span>
                    {p.stuck && <span className="db-st CANCELLED od-ml">Treo</span>}
                  </td>
                  <td className="num">{money(p.amount)}</td>
                  <td className="small">{vnpayLabel(p.providerResponseCode)}</td>
                  <td className="small">{p.lastCheck ? `${p.lastCheck.action} · ${fmtStamp(p.lastCheck.checkedAt)}` : '—'}</td>
                  <td className="small">{fmtDateTime(p.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!loading && items.length === 0 && <div className="a-empty">Không có giao dịch nào trong mục này.</div>}
        <div className="a-pager">
          <span>Đang hiện <b>{fmtNumber(items.length)}</b> giao dịch, mới nhất trước</span>
          {next && <button type="button" className="a-btn sm" disabled={loading} onClick={() => load(next)}>{loading ? 'Đang tải…' : 'Tải thêm'}</button>}
        </div>
      </div>

      {open && <OrderDetail bookingId={open} onClose={() => setOpen(null)} />}
    </AdminLayout>
  );
}
