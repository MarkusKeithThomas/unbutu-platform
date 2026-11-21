import { useEffect, useMemo, useState } from 'react';
import { catalog, errorMessage, type CatalogEvent, type EventInput, type EventType } from '../api';
import { useAuth } from '../auth';
import { AdminLayout, Confirm, Drawer, Pager, PlusIcon, SearchBox, fmtDateTime, fmtStamp, usePaged, useToast } from './ui';

const TYPES: Record<EventType, string> = { CONCERT: 'Ca nhạc', MOVIE: 'Phim', SPORT: 'Thể thao', THEATER: 'Sân khấu' };

type Form = { title: string; type: EventType; venue: string; startsAt: string; description: string };

/** ISO (UTC) → giá trị cho <input type="datetime-local"> theo giờ máy người dùng. */
function toLocalInput(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function EventsPage() {
  const { withToken } = useAuth();
  const toast = useToast();
  const [items, setItems] = useState<CatalogEvent[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState<CatalogEvent | 'new' | null>(null);
  const [form, setForm] = useState<Form | null>(null);
  const [tried, setTried] = useState(false);
  const [deleting, setDeleting] = useState<CatalogEvent | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () =>
    catalog.events().then(
      (ev) => { setItems(ev); setLoadError(null); },
      (e) => setLoadError(errorMessage(e)),
    );
  useEffect(() => { void load(); }, []);

  const list = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (items ?? []).filter((e) => !q || e.title.toLowerCase().includes(q) || (e.venue ?? '').toLowerCase().includes(q));
  }, [items, query]);
  const paged = usePaged(list, 10);

  const open = (e: CatalogEvent | 'new') => {
    setEditing(e);
    setTried(false);
    setForm(e === 'new'
      ? { title: '', type: 'CONCERT', venue: '', startsAt: '', description: '' }
      : { title: e.title, type: e.type, venue: e.venue ?? '', startsAt: toLocalInput(e.startsAt), description: e.description ?? '' });
  };
  const close = () => { setEditing(null); setForm(null); };

  const f = form ?? { title: '', type: 'CONCERT' as EventType, venue: '', startsAt: '', description: '' };
  const errTitle = tried && !f.title.trim();
  const set = (k: keyof Form) => (v: string) => setForm((old) => (old ? { ...old, [k]: v } : old));

  const save = async () => {
    setTried(true);
    if (!f.title.trim()) return;
    const input: EventInput = {
      title: f.title.trim(), type: f.type, venue: f.venue.trim() || null, description: f.description.trim() || null,
      startsAt: f.startsAt ? new Date(f.startsAt).toISOString() : null,
    };
    setBusy(true);
    try {
      if (editing === 'new') {
        const created = await withToken((t) => catalog.createEvent(t, input));
        setItems((old) => [created, ...(old ?? [])]);
        paged.setPage(0);
        toast.show(`Đã tạo sự kiện “${created.title}”`);
      } else if (editing) {
        const updated = await withToken((t) => catalog.updateEvent(t, editing.id, input));
        setItems((old) => (old ?? []).map((x) => (x.id === updated.id ? updated : x)));
        toast.show(`Đã lưu “${updated.title}”`);
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
    const ev = deleting;
    setBusy(true);
    try {
      await withToken((t) => catalog.deleteEvent(t, ev.id));
      setItems((old) => (old ?? []).filter((x) => x.id !== ev.id));
      toast.show(`Đã xoá “${ev.title}”`);
    } catch (e) {
      toast.show(errorMessage(e), true);
    } finally {
      setDeleting(null);
      setBusy(false);
    }
  };

  return (
    <AdminLayout section="Sự kiện" title="Sự kiện" action={
      <button type="button" className="a-btn primary" onClick={() => open('new')}><PlusIcon />Thêm sự kiện</button>
    }>
      <div className="a-panel a-toolbar">
        <SearchBox id="ev-q" label="Tìm theo tên hoặc địa điểm" value={query} onChange={(v) => { setQuery(v); paged.setPage(0); }} />
      </div>

      <div className="a-panel" style={{ overflow: 'hidden' }}>
        <div className="a-table-wrap">
          <table className="a-table">
            <thead>
              <tr>
                <th scope="col">Sự kiện</th>
                <th scope="col">Loại</th>
                <th scope="col">Địa điểm</th>
                <th scope="col">Bắt đầu</th>
                <th scope="col">Cập nhật</th>
                <th scope="col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {paged.slice.map((e) => (
                <tr key={e.id}>
                  <td className="strong">{e.title}</td>
                  <td className="soft">{TYPES[e.type]}</td>
                  <td className="soft">{e.venue ?? '—'}</td>
                  <td className="soft">{fmtDateTime(e.startsAt)}</td>
                  <td className="small">{fmtStamp(e.updatedAt)}</td>
                  <td>
                    <button type="button" className="a-btn sm" aria-label={`Sửa ${e.title}`} onClick={() => open(e)}>Sửa</button>
                    <button type="button" className="a-btn sm danger-outline" aria-label={`Xoá ${e.title}`} onClick={() => setDeleting(e)}>Xoá</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loadError && <div className="a-empty">{loadError} <button type="button" className="btn-link" onClick={() => void load()}>Thử lại</button></div>}
        {!loadError && items === null && <div className="a-empty">Đang tải…</div>}
        {!loadError && items !== null && list.length === 0 && (
          <div className="a-empty">{items.length === 0 ? 'Chưa có sự kiện nào.' : 'Không có sự kiện nào khớp tìm kiếm.'}</div>
        )}
        <Pager total={list.length} noun="sự kiện" paged={paged} />
      </div>

      {editing !== null && (
        <Drawer title={editing === 'new' ? 'Thêm sự kiện' : 'Sửa sự kiện'} saveLabel={editing === 'new' ? 'Tạo sự kiện' : 'Lưu thay đổi'}
          busy={busy} onClose={close} onSave={() => void save()}>
          <div className="field">
            <label htmlFor="ev-title">Tên sự kiện *</label>
            <input id="ev-title" className={`a-input${errTitle ? ' invalid' : ''}`} value={f.title} maxLength={255}
              onChange={(e) => set('title')(e.target.value)} placeholder="VD: Đêm nhạc 20/12" aria-invalid={errTitle} autoFocus />
            {errTitle && <span className="field-error">Bắt buộc nhập tên.</span>}
          </div>
          <div className="a-grid2">
            <div className="field">
              <label htmlFor="ev-type">Loại</label>
              <select id="ev-type" className="a-input" value={f.type} onChange={(e) => set('type')(e.target.value)}>
                {(Object.keys(TYPES) as EventType[]).map((k) => <option key={k} value={k}>{TYPES[k]}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="ev-start">Bắt đầu</label>
              <input id="ev-start" className="a-input" type="datetime-local" value={f.startsAt} onChange={(e) => set('startsAt')(e.target.value)} />
            </div>
          </div>
          <div className="field">
            <label htmlFor="ev-venue">Địa điểm</label>
            <input id="ev-venue" className="a-input" value={f.venue} maxLength={255} onChange={(e) => set('venue')(e.target.value)}
              placeholder="VD: Nhà hát Hòa Bình" />
          </div>
          <div className="field">
            <label htmlFor="ev-desc">Mô tả</label>
            <textarea id="ev-desc" className="a-input" value={f.description} maxLength={2000} onChange={(e) => set('description')(e.target.value)} />
          </div>
        </Drawer>
      )}

      {deleting && (
        <Confirm title={`Xoá sự kiện “${deleting.title}”?`} confirmLabel="Xoá" busy={busy}
          message="Chỉ xoá được sự kiện chưa có hạng vé. Thao tác này không hoàn tác được."
          onCancel={() => setDeleting(null)} onConfirm={() => void doDelete()} />
      )}
      {toast.node}
    </AdminLayout>
  );
}
