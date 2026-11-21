import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { catalog, errorMessage, type CatalogEvent, type TicketStatus, type TicketType, type TicketTypeInput } from '../api';
import { useAuth } from '../auth';
import { AdminLayout, Confirm, Drawer, Pager, PlusIcon, SearchBox, fmtNumber, fmtStamp, usePaged, useToast } from './ui';

const STATUS: Record<TicketStatus, string> = { ON_SALE: 'Đang bán', PAUSED: 'Tạm dừng', DRAFT: 'Nháp' };
const FILTERS: Array<TicketStatus | 'ALL'> = ['ALL', 'ON_SALE', 'PAUSED', 'DRAFT'];

type Form = { eventId: string; name: string; price: string; total: string; status: TicketStatus };

export function TicketTypesPage() {
  const { withToken } = useAuth();
  const toast = useToast();
  const [items, setItems] = useState<TicketType[] | null>(null);
  const [events, setEvents] = useState<CatalogEvent[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<TicketStatus | 'ALL'>('ALL');
  const [editing, setEditing] = useState<TicketType | 'new' | null>(null);
  const [form, setForm] = useState<Form | null>(null);
  const [tried, setTried] = useState(false);
  const [deleting, setDeleting] = useState<TicketType | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [tt, ev] = await Promise.all([withToken(catalog.ticketTypes), catalog.events()]);
      setItems(tt);
      setEvents(ev);
      setLoadError(null);
    } catch (e) {
      setLoadError(errorMessage(e));
    }
  }, [withToken]);
  useEffect(() => {
    void load();
    // Chỉ tải lúc mở trang (withToken là hàm mới mỗi lần render).
  }, []);

  const list = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (items ?? []).filter(
      (t) => (filter === 'ALL' || t.status === filter) && (!q || t.name.toLowerCase().includes(q) || t.code.toLowerCase().includes(q)),
    );
  }, [items, query, filter]);
  const paged = usePaged(list, 10);

  const openNew = () => {
    setEditing('new');
    setTried(false);
    setForm({ eventId: events[0]?.id ?? '', name: '', price: '', total: '', status: 'DRAFT' });
  };
  const openEdit = (t: TicketType) => {
    setEditing(t);
    setTried(false);
    setForm({ eventId: t.eventId, name: t.name, price: String(t.price), total: String(t.totalQuantity), status: t.status });
  };
  const close = () => {
    setEditing(null);
    setForm(null);
  };

  const f = form ?? { eventId: '', name: '', price: '', total: '', status: 'DRAFT' as TicketStatus };
  const published = editing !== null && editing !== 'new' && !editing.deletable; // đã từng mở bán
  const errName = tried && !f.name.trim();
  const errEvent = tried && !f.eventId;
  const errPrice = tried && !(Number.isInteger(Number(f.price)) && Number(f.price) > 0);
  const errTotal = tried && !(f.total !== '' && Number.isInteger(Number(f.total)) && Number(f.total) >= 0);
  const set = (k: keyof Form) => (v: string) => setForm((old) => (old ? { ...old, [k]: v } : old));

  const save = async () => {
    setTried(true);
    if (!f.name.trim() || !f.eventId || !(Number(f.price) > 0) || !Number.isInteger(Number(f.price))
      || f.total === '' || !Number.isInteger(Number(f.total)) || Number(f.total) < 0) return;
    const input: TicketTypeInput = { eventId: f.eventId, name: f.name.trim(), price: Number(f.price), totalQuantity: Number(f.total), status: f.status };
    setBusy(true);
    try {
      if (editing === 'new') {
        const created = await withToken((t) => catalog.createTicketType(t, input));
        setItems((old) => [created, ...(old ?? [])]);
        setFilter('ALL');
        paged.setPage(0);
        toast.show(`Đã tạo hạng vé “${created.name}”`);
      } else if (editing) {
        const updated = await withToken((t) => catalog.updateTicketType(t, editing.id, input));
        setItems((old) => (old ?? []).map((x) => (x.id === updated.id ? updated : x)));
        toast.show(`Đã lưu “${updated.name}”`);
      }
      close();
    } catch (e) {
      toast.show(errorMessage(e), true);
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    if (!deleting) return;
    const t0 = deleting;
    setBusy(true);
    try {
      if (t0.deletable) {
        await withToken((t) => catalog.deleteTicketType(t, t0.id));
        setItems((old) => (old ?? []).filter((x) => x.id !== t0.id));
        toast.show(`Đã xoá “${t0.name}”`);
      } else {
        // Đã từng mở bán → giữ lại (lịch sử đơn), chỉ chuyển Tạm dừng.
        const input: TicketTypeInput = { eventId: t0.eventId, name: t0.name, price: t0.price, totalQuantity: t0.totalQuantity, status: 'PAUSED' };
        const updated = await withToken((t) => catalog.updateTicketType(t, t0.id, input));
        setItems((old) => (old ?? []).map((x) => (x.id === updated.id ? updated : x)));
        toast.show(`Đã tạm dừng “${t0.name}”`);
      }
      setDeleting(null);
    } catch (e) {
      toast.show(errorMessage(e), true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AdminLayout section="Hạng vé" title="Hạng vé" action={
      <button type="button" className="a-btn primary" onClick={openNew} disabled={items === null}><PlusIcon />Thêm hạng vé</button>
    }>
      <div className="a-panel a-toolbar">
        <SearchBox id="tt-q" label="Tìm theo tên hoặc mã" value={query} onChange={(v) => { setQuery(v); paged.setPage(0); }} />
        <div role="group" aria-label="Lọc trạng thái" className="a-chips">
          {FILTERS.map((k) => (
            <button key={k} type="button" className="a-chip" aria-pressed={filter === k} onClick={() => { setFilter(k); paged.setPage(0); }}>
              {k === 'ALL' ? 'Tất cả' : STATUS[k]} ({(items ?? []).filter((t) => k === 'ALL' || t.status === k).length})
            </button>
          ))}
        </div>
      </div>

      <div className="a-panel" style={{ overflow: 'hidden' }}>
        <div className="a-table-wrap">
          <table className="a-table">
            <thead>
              <tr>
                <th scope="col">Mã</th>
                <th scope="col">Hạng vé</th>
                <th scope="col">Sự kiện</th>
                <th scope="col" className="num">Giá</th>
                <th scope="col" className="num">Tổng số vé</th>
                <th scope="col">Trạng thái</th>
                <th scope="col">Cập nhật</th>
                <th scope="col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {paged.slice.map((t) => (
                <tr key={t.id}>
                  <td className="code">{t.code}</td>
                  <td className="strong">{t.name}</td>
                  <td className="soft">{t.eventTitle}</td>
                  <td className="num strong">{fmtNumber(t.price)} đ</td>
                  <td className="num">{fmtNumber(t.totalQuantity)}</td>
                  <td><span className={`badge ${t.status}`}>{STATUS[t.status]}</span></td>
                  <td className="small">{fmtStamp(t.updatedAt)}</td>
                  <td>
                    <button type="button" className="a-btn sm" aria-label={`Sửa ${t.name}`} onClick={() => openEdit(t)}>Sửa</button>
                    <button type="button" className="a-btn sm danger-outline" aria-label={`Xoá ${t.name}`} onClick={() => setDeleting(t)}>Xoá</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loadError && <div className="a-empty">{loadError} <button type="button" className="btn-link" onClick={() => void load()}>Thử lại</button></div>}
        {!loadError && items === null && <div className="a-empty">Đang tải…</div>}
        {!loadError && items !== null && list.length === 0 && (
          <div className="a-empty">{items.length === 0 ? 'Chưa có hạng vé nào.' : 'Không có hạng vé nào khớp bộ lọc.'}</div>
        )}
        <Pager total={list.length} noun="hạng vé" paged={paged} />
      </div>

      {editing !== null && (
        <Drawer title={editing === 'new' ? 'Thêm hạng vé' : 'Sửa hạng vé'} saveLabel={editing === 'new' ? 'Tạo hạng vé' : 'Lưu thay đổi'}
          busy={busy} onClose={close} onSave={() => void save()}>
          <div className="field">
            <label htmlFor="fm-name">Tên hạng vé *</label>
            <input id="fm-name" className={`a-input${errName ? ' invalid' : ''}`} value={f.name} onChange={(e) => set('name')(e.target.value)}
              placeholder="VD: VIP" maxLength={100} aria-invalid={errName} autoFocus />
            {errName && <span className="field-error">Bắt buộc nhập tên.</span>}
          </div>
          <div className="field">
            <label htmlFor="fm-event">Sự kiện *</label>
            {events.length === 0 ? (
              <p className="a-note">Chưa có sự kiện nào. <Link to="/admin/events">Tạo sự kiện</Link> trước.</p>
            ) : (
              <select id="fm-event" className={`a-input${errEvent ? ' invalid' : ''}`} value={f.eventId} disabled={published}
                onChange={(e) => set('eventId')(e.target.value)}>
                {events.map((ev) => <option key={ev.id} value={ev.id}>{ev.title}</option>)}
              </select>
            )}
          </div>
          <div className="a-grid2">
            <div className="field">
              <label htmlFor="fm-price">Giá (VND) *</label>
              <input id="fm-price" className={`a-input${errPrice ? ' invalid' : ''}`} type="number" min={1} step={1000} inputMode="numeric"
                value={f.price} onChange={(e) => set('price')(e.target.value)} aria-invalid={errPrice} />
              {errPrice && <span className="field-error">Giá phải lớn hơn 0.</span>}
            </div>
            <div className="field">
              <label htmlFor="fm-total">Tổng số vé *</label>
              <input id="fm-total" className={`a-input${errTotal ? ' invalid' : ''}`} type="number" min={0} inputMode="numeric"
                value={f.total} onChange={(e) => set('total')(e.target.value)} aria-invalid={errTotal} />
              {errTotal && <span className="field-error">Nhập số nguyên ≥ 0.</span>}
            </div>
          </div>
          <fieldset className="a-fieldset">
            <legend>Trạng thái</legend>
            <div className="a-radios">
              {(['ON_SALE', 'PAUSED', 'DRAFT'] as TicketStatus[]).map((s) => {
                const off = published && s === 'DRAFT';
                return (
                  <label key={s} className={`a-radio${f.status === s ? ' on' : ''}${off ? ' off' : ''}`}>
                    <input type="radio" name="fm-status" checked={f.status === s} disabled={off} onChange={() => set('status')(s)} />
                    {STATUS[s]}
                  </label>
                );
              })}
            </div>
          </fieldset>
          {published && (
            <p className="a-note">Hạng vé đã từng mở bán: không đổi sự kiện, không chuyển về Nháp và không xoá hẳn được (giữ lịch sử đơn hàng).</p>
          )}
        </Drawer>
      )}

      {deleting && (
        deleting.deletable ? (
          <Confirm title={`Xoá hạng vé “${deleting.name}”?`} message="Thao tác này không hoàn tác được." confirmLabel="Xoá"
            busy={busy} onCancel={() => setDeleting(null)} onConfirm={() => void doDelete()} />
        ) : deleting.status === 'PAUSED' ? (
          <Confirm title={`Không xoá được “${deleting.name}”`} busy={busy} onCancel={() => setDeleting(null)}
            message="Hạng vé này đã từng mở bán nên được giữ lại để bảo toàn lịch sử đơn hàng. Hiện đang Tạm dừng – người mua không đặt được." />
        ) : (
          <Confirm title={`Xoá hạng vé “${deleting.name}”?`} confirmLabel="Chuyển sang Tạm dừng" busy={busy}
            message="Hạng vé này đã từng mở bán nên sẽ được chuyển sang Tạm dừng thay vì xoá hẳn, để giữ lịch sử đơn hàng."
            onCancel={() => setDeleting(null)} onConfirm={() => void doDelete()} />
        )
      )}
      {toast.node}
    </AdminLayout>
  );
}
