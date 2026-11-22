import { useEffect, useRef, useState } from 'react';
import { ApiError, errorMessage, loadtest, type LtConfig, type LtDetail, type LtRun, type LtStatus, type LtStep } from '../api';
import { useAuth } from '../auth';
import { LineChart } from './LineChart';
import { AdminLayout, Confirm, fmtNumber, fmtStamp, useToast } from './ui';

const SERIES_1 = '#2a78d6';   // dataviz palette – slot 1 (blue)
const SIGNOZ_URL = 'https://unbutu.tail3c11f4.ts.net:8443';   // chỉ mở được trong tailnet (ADR-0006)

const STATUS: Record<LtStatus, string> = {
  RUNNING: 'Đang chạy', COMPLETED: 'Xong', STOPPED: 'Đã dừng tay', ERROR: 'Lỗi', ABORTED: 'Bị ngắt (service restart)',
};

type Form = { scenario: string; steps: string; stepSeconds: string; p95Ms: string; maxErrPct: string; stopOnFail: boolean };

/** "50, 100,200" → [50,100,200]; null nếu sai. */
function parseSteps(s: string): number[] | null {
  const parts = s.split(',').map((p) => p.trim()).filter(Boolean);
  if (!parts.length || parts.some((p) => !/^\d+$/.test(p))) return null;
  return parts.map(Number);
}

/** Lỗi 400/409 của loadtest-service có câu giải thích riêng (detail) – hiện thẳng. */
const ltError = (e: unknown) => (e instanceof ApiError && (e.status === 400 || e.status === 409) ? e.message : errorMessage(e));

export function LoadTestPage() {
  // withToken đổi mỗi lần render → giữ bản mới nhất trong ref (không đưa vào deps của effect – tránh gọi API lặp vô hạn).
  const auth = useAuth();
  const tokenRef = useRef(auth.withToken);
  tokenRef.current = auth.withToken;
  const withToken: typeof auth.withToken = (call) => tokenRef.current(call);
  const toast = useToast();
  const [config, setConfig] = useState<LtConfig | null>(null);
  const [history, setHistory] = useState<LtRun[]>([]);
  const [detail, setDetail] = useState<LtDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [form, setForm] = useState<Form>({ scenario: 'browse', steps: '50,100,200', stepSeconds: '30', p95Ms: '300', maxErrPct: '1', stopOnFail: true });
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const watching = useRef<string | null>(null);   // id lượt đang theo dõi trực tiếp

  const loadHistory = () => withToken(loadtest.runs).then(setHistory);

  // Lần đầu: cấu hình + lịch sử + lượt đang chạy (nếu có, kể cả do người khác bấm).
  useEffect(() => {
    Promise.all([withToken(loadtest.config), withToken(loadtest.runs), withToken(loadtest.current)]).then(
      ([c, h, cur]) => {
        setConfig(c);
        setHistory(h);
        if (cur) { setDetail(cur); watching.current = cur.run.id; }
        else if (h.length) void withToken((t) => loadtest.run(t, h[0].id)).then(setDetail);
      },
      (e) => setLoadError(errorMessage(e)),
    );
  }, []);

  // Đang chạy: hỏi lại mỗi 2 s. Hết chạy (204) → đọc bản đã lưu (bảng chính thức của k6) + lịch sử.
  const running = detail?.run.status === 'RUNNING';
  useEffect(() => {
    if (!running) return;
    const id = window.setInterval(async () => {
      try {
        const cur = await withToken(loadtest.current);
        if (cur && cur.run.id === watching.current) { setDetail(cur); return; }
        const done = watching.current;
        watching.current = null;
        if (done) setDetail(await withToken((t) => loadtest.run(t, done)));
        void loadHistory();
      } catch { /* mạng chập chờn – lần sau thử lại */ }
    }, 2000);
    return () => window.clearInterval(id);
  }, [running]);

  const steps = parseSteps(form.steps);
  const stepSec = Number(form.stepSeconds);
  const maxRps = config?.limits.maxRps ?? 0;
  const scenario = config?.scenarios.find((s) => s.name === form.scenario);
  const maxSec = Math.min(config?.limits.maxDurationSeconds ?? 0, scenario?.maxDurationSeconds ?? Infinity);
  const formError =
    !steps ? 'Bậc rps dạng 50,100,200'
      : steps.some((s, i) => i > 0 && s <= steps[i - 1]) ? 'Các bậc phải tăng dần'
      : steps.some((s) => s < 1 || s > maxRps) ? `Mỗi bậc từ 1 đến ${fmtNumber(maxRps)} rps`
      : !(stepSec >= 10 && stepSec <= 600) ? 'Thời gian mỗi bậc 10–600 giây'
      : config && steps.length * stepSec > maxSec ? `Tổng thời gian vượt ${maxSec / 60} phút của kịch bản này`
      : null;

  const start = async () => {
    if (!steps) return;
    setBusy(true);
    try {
      const run = await withToken((t) => loadtest.start(t, {
        scenario: form.scenario, steps, stepSeconds: stepSec, p95Ms: Number(form.p95Ms), maxErrPct: Number(form.maxErrPct), stopOnFail: form.stopOnFail,
      }));
      watching.current = run.id;
      setDetail({ run, stepViews: steps.map((rps) => ({ rps, state: 'PENDING', reqs: 0, achievedRps: null, p95: null, p99: null, errPct: null, dropped: 0, pass: null, ttqP95: null, ticketPct: null })), samples: [], logTail: null });
      void loadHistory();
      toast.show('Đã bắt đầu – số liệu cập nhật mỗi 2 giây');
    } catch (e) {
      toast.show(ltError(e), true);
    } finally {
      setBusy(false);
      setConfirming(false);
    }
  };

  const stop = async () => {
    if (!detail) return;
    try {
      await withToken((t) => loadtest.stop(t, detail.run.id));
      toast.show('Đang dừng – k6 vẫn chấm điểm các bậc đã chạy');
    } catch (e) {
      toast.show(ltError(e), true);
    }
  };

  const open = async (id: string) => {
    try { setDetail(await withToken((t) => loadtest.run(t, id))); } catch (e) { toast.show(ltError(e), true); }
  };

  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.type === 'checkbox' ? (e.target as HTMLInputElement).checked : e.target.value }));

  return (
    <AdminLayout section="Load test" title="Load test"
      action={running ? <button type="button" className="a-btn danger" onClick={stop}>Dừng lượt đang chạy</button> : undefined}>
      {loadError && <div className="a-panel lt-pad lt-err" role="alert">{loadError}</div>}

      <section className="a-panel lt-pad" aria-labelledby="lt-new">
        <h2 id="lt-new" className="lt-h2">Chạy lượt mới</h2>
        <p className="lt-note">
          k6 chạy trên server, bắn thẳng vào api-gateway (không qua Internet). Mỗi bậc giữ đúng N request/giây;
          bậc <b>PASS</b> khi p95 &lt; ngưỡng, lỗi &lt; ngưỡng và bỏ lượt &lt; 5%. Kết luận = bậc cao nhất PASS liên tiếp.
        </p>
        <form className="lt-form" onSubmit={(e) => { e.preventDefault(); if (!formError) setConfirming(true); }}>
          <label>Kịch bản
            <select value={form.scenario} onChange={set('scenario')}>
              {config?.scenarios.map((s) => <option key={s.name} value={s.name}>{s.title} ({s.name})</option>)}
            </select>
          </label>
          <label>Các bậc ({scenario?.unit ?? 'rps'})
            <input value={form.steps} onChange={set('steps')} inputMode="numeric" placeholder="50,100,200" aria-invalid={!!formError} />
          </label>
          <label>Mỗi bậc (giây)<input type="number" min={10} max={600} value={form.stepSeconds} onChange={set('stepSeconds')} /></label>
          <label>p95 tối đa (ms)<input type="number" min={10} max={10000} value={form.p95Ms} onChange={set('p95Ms')} /></label>
          <label>Lỗi tối đa (%)<input type="number" min={0} max={50} step={0.1} value={form.maxErrPct} onChange={set('maxErrPct')} /></label>
          <label className="lt-check"><input type="checkbox" checked={form.stopOnFail} onChange={set('stopOnFail')} />Dừng ngay khi 1 bậc FAIL</label>
          <div className="lt-submit">
            <button type="submit" className="a-btn primary" disabled={!!formError || running || !config}>Chạy load test</button>
            <span className={formError ? 'lt-err' : 'lt-muted'}>
              {formError ?? (steps && `Tổng ~${Math.ceil((steps.length * stepSec) / 60)} phút · trần ${fmtNumber(maxRps)} rps`)}
            </span>
          </div>
        </form>
        {scenario && <p className={scenario.writes ? 'lt-desc lt-warn' : 'lt-muted lt-desc'}>{scenario.writes && <b>Ghi dữ liệu thật vào prod. </b>}{scenario.description}</p>}
      </section>

      {detail && <RunView detail={detail} unit={config?.scenarios.find((s) => s.name === detail.run.scenario)?.unit ?? 'rps'} />}

      <section className="a-panel" aria-labelledby="lt-hist">
        <h2 id="lt-hist" className="lt-h2 lt-pad">Lịch sử</h2>
        <div className="a-table-wrap">
          <table className="a-table">
            <thead><tr><th>Bắt đầu</th><th>Kịch bản</th><th>Các bậc rps</th><th>Mỗi bậc</th><th>Kết luận</th><th>Trạng thái</th><th>Người chạy</th><th /></tr></thead>
            <tbody>
              {history.map((r) => (
                <tr key={r.id} className={detail?.run.id === r.id ? 'lt-sel' : undefined}>
                  <td>{fmtStamp(r.startedAt)}</td>
                  <td className="code">{r.scenario}</td>
                  <td className="code">{r.steps.join(', ')}</td>
                  <td>{r.stepSeconds}s</td>
                  <td className="strong">{r.passUpToRps ? `${fmtNumber(r.passUpToRps)}/s` : r.status === 'RUNNING' ? '…' : '—'}</td>
                  <td>{STATUS[r.status]}</td>
                  <td className="small">{r.startedBy}</td>
                  <td><button type="button" className="a-btn sm" onClick={() => open(r.id)}>Xem</button></td>
                </tr>
              ))}
              {!history.length && <tr><td colSpan={8} className="small">Chưa có lượt nào.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      {confirming && steps && (
        <Confirm title="Bắn tải vào hệ thống?" busy={busy} confirmLabel="Chạy" onCancel={() => setConfirming(false)} onConfirm={start}
          message={`Kịch bản ${form.scenario}, bậc ${steps.join(' → ')} rps, mỗi bậc ${stepSec}s. Khách thật có thể thấy hệ thống chậm trong lúc chạy – có thể dừng bất cứ lúc nào.`} />
      )}
      {toast.node}
    </AdminLayout>
  );
}

function Verdict({ step }: { step: LtStep }) {
  if (step.state === 'PENDING') return <span className="lt-muted">Chờ</span>;
  if (step.state === 'SKIPPED') return <span className="lt-muted">Không chạy</span>;
  if (step.pass == null) return <span className="lt-badge run">● Đang chạy</span>;
  return step.pass ? <span className="lt-badge ok">✓ PASS</span> : <span className="lt-badge bad">✕ FAIL</span>;
}

function RunView({ detail, unit }: { detail: LtDetail; unit: string }) {
  const { run, stepViews, samples } = detail;
  const purchase = stepViews.some((s) => s.ttqP95 != null || s.ticketPct != null) || run.scenario === 'purchase';
  const live = run.status === 'RUNNING';
  const last = samples[samples.length - 1];
  const firstFail = stepViews.find((s) => s.pass === false);
  const passUpTo = live
    ? (() => { let v = 0; for (const s of stepViews) { if (s.pass === true) v = s.rps; else break; } return v; })()
    : run.passUpToRps ?? 0;
  const x = samples.map((s) => s.t);

  return (
    <section className="a-panel lt-pad lt-run" aria-labelledby="lt-run-h">
      <div className="lt-run-head">
        <h2 id="lt-run-h" className="lt-h2">
          {live ? 'Đang chạy' : 'Kết quả'} · <span className="code">{run.scenario}</span> · {fmtStamp(run.startedAt)}
        </h2>
        <span className="lt-muted">{STATUS[run.status]} · tiêu chí: p95 &lt; {run.p95Ms} ms, lỗi &lt; {run.maxErrPct}%, mỗi bậc {run.stepSeconds}s</span>
      </div>

      <div className="lt-tiles">
        <div className="lt-tile hero">
          <small>Chịu được</small>
          <strong>{passUpTo ? `${fmtNumber(passUpTo)}` : '—'}</strong>
          <span>{passUpTo ? `${unit} · ` : ''}{firstFail ? `bậc ${fmtNumber(firstFail.rps)} FAIL` : live ? 'tạm tính theo các bậc đã xong' : passUpTo ? 'mọi bậc đều PASS – thử bậc cao hơn' : 'chưa có bậc nào PASS'}</span>
        </div>
        <div className="lt-tile"><small>{unit} thực tế</small><strong>{last ? fmtNumber(Math.round(last.rps)) : '—'}</strong><span>mục tiêu {last ? fmtNumber(last.stepRps) : '—'}{purchase && last ? ` · ${fmtNumber(Math.round(last.httpRps))} request/giây` : ''}</span></div>
        {purchase && <div className="lt-tile"><small>Tới lúc có QR (p95)</small><strong>{last?.ttqP95 != null ? `${fmtNumber(Math.round(last.ttqP95))} ms` : '—'}</strong><span>bấm mua → vé QR, bậc hiện tại</span></div>}
        <div className="lt-tile"><small>p95 bậc hiện tại</small><strong>{last?.p95 != null ? `${fmtNumber(last.p95)} ms` : '—'}</strong><span>p99 {last?.p99 != null ? `${fmtNumber(last.p99)} ms` : '—'}</span></div>
        <div className="lt-tile"><small>Lỗi bậc hiện tại</small><strong>{last ? `${last.errPct.toLocaleString('vi-VN', { maximumFractionDigits: 2 })}%` : '—'}</strong><span>{last ? `${fmtNumber(last.vus)} người dùng ảo` : ''}</span></div>
      </div>

      <div className="lt-charts">
        <LineChart title={unit.charAt(0).toUpperCase() + unit.slice(1)} x={x} empty={live ? 'k6 đang khởi động…' : 'Không có số liệu'} series={[
          { name: 'thực tế', values: samples.map((s) => s.rps), color: SERIES_1, unit: '/s' },
          { name: 'mục tiêu của bậc', values: samples.map((s) => s.stepRps), muted: true, unit: '/s' },
        ]} />
        <LineChart title="Độ trễ p95 (ms, dồn từ đầu bậc)" x={x} empty={live ? 'k6 đang khởi động…' : 'Không có số liệu'}
          threshold={{ value: run.p95Ms, label: `ngưỡng ${run.p95Ms} ms` }}
          series={[{ name: 'p95', values: samples.map((s) => s.p95), color: SERIES_1, unit: 'ms' }]} />
        {purchase && (
          <LineChart title="Thời gian bấm mua → có vé QR, p95 (ms, dồn từ đầu bậc)" x={x} empty={live ? 'Chờ đơn đầu tiên có QR…' : 'Không có số liệu'}
            series={[{ name: 'tới lúc có QR p95', values: samples.map((s) => s.ttqP95), color: SERIES_1, unit: 'ms' }]} />
        )}
      </div>

      <div className="a-table-wrap">
        <table className="a-table lt-steps">
          <thead><tr><th>Bậc</th><th className="num">Đạt /giây</th><th className="num">Số lượt</th><th className="num">p95</th><th className="num">p99</th><th className="num">Lỗi</th><th className="num">Bỏ lượt</th>{purchase && <><th className="num">Có QR p95</th><th className="num">% đơn có vé</th></>}<th>Kết quả</th></tr></thead>
          <tbody>
            {stepViews.map((s) => (
              <tr key={s.rps}>
                <td className="strong">{fmtNumber(s.rps)}/s</td>
                <td className="num">{s.achievedRps != null ? fmtNumber(Math.round(s.achievedRps)) : '—'}</td>
                <td className="num">{s.reqs ? fmtNumber(s.reqs) : '—'}</td>
                <td className="num">{s.p95 != null ? `${fmtNumber(s.p95)} ms` : '—'}</td>
                <td className="num">{s.p99 != null ? `${fmtNumber(s.p99)} ms` : '—'}</td>
                <td className="num">{s.errPct != null ? `${s.errPct.toLocaleString('vi-VN', { maximumFractionDigits: 2 })}%` : '—'}</td>
                <td className="num">{s.state === 'PENDING' || s.state === 'SKIPPED' ? '—' : fmtNumber(s.dropped)}</td>
                {purchase && <>
                  <td className="num">{s.ttqP95 != null ? `${fmtNumber(Math.round(s.ttqP95))} ms` : '—'}</td>
                  <td className="num">{s.ticketPct != null ? `${s.ticketPct.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%` : '—'}</td>
                </>}
                <td><Verdict step={s} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <p className="lt-muted">
        Nghẽn ở đâu (CPU từng container, p99 từng service, pool DB, GC): SigNoz dashboard <b>Service RED + JVM</b> và <b>Container</b> –{' '}
        <a href={SIGNOZ_URL} target="_blank" rel="noreferrer">mở SigNoz</a> (chỉ trong tailnet).
      </p>
      {detail.logTail && (
        <details className="lt-log">
          <summary>Log k6 (cuối)</summary>
          <pre>{detail.logTail}</pre>
        </details>
      )}
    </section>
  );
}
