import { useEffect, useRef, useState } from 'react';

/** Một đường trên biểu đồ. `muted`: đường tham chiếu (mục tiêu) – xám, nét đứt, không dùng màu series. */
export type Series = { name: string; values: (number | null)[]; color?: string; muted?: boolean; unit: string };

type Props = {
  title: string;
  x: number[];                      // giây kể từ lúc bắt đầu (hoặc chỉ số ngày khi có xFormat)
  series: Series[];
  threshold?: { value: number; label: string };
  height?: number;
  empty?: string;
  /** Nhãn trục X/tooltip; có thì x là chỉ số rời rạc (0..n-1, vd. ngày) – mặc định mm:ss. */
  xFormat?: (v: number) => string;
};

const PAD = { top: 12, right: 16, bottom: 28, left: 52 };

/** Bước chia trục "đẹp" (1, 2, 5 × 10^n). */
function niceMax(v: number): { max: number; step: number } {
  if (v <= 0) return { max: 1, step: 0.25 };
  const raw = v / 4;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? 10 * mag;
  return { max: Math.ceil(v / step) * step, step };
}

const fmt = (v: number) => (v >= 100 ? Math.round(v).toLocaleString('vi-VN') : v.toLocaleString('vi-VN', { maximumFractionDigits: 1 }));
const fmtT = (s: number) => `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, '0')}`;

/** Biểu đồ đường SVG thuần (không thư viện): 1 trục Y, crosshair + tooltip liệt kê mọi series tại điểm gần nhất. */
export function LineChart({ title, x, series, threshold, height = 220, empty = 'Chưa có số liệu', xFormat }: Props) {
  const wrap = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(600);
  const [hover, setHover] = useState<number | null>(null);

  useEffect(() => {
    const el = wrap.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setWidth(Math.max(280, e.contentRect.width)));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const named = series.filter((s) => !s.muted);
  const all = series.flatMap((s) => s.values).filter((v): v is number => v != null);
  if (threshold) all.push(threshold.value);
  const { max, step } = niceMax(Math.max(0, ...all) * 1.05);
  const xMax = xFormat ? Math.max(1, x.length ? x[x.length - 1] : 1) : Math.max(10, x.length ? x[x.length - 1] : 10);
  const fx = xFormat ?? fmtT;
  const w = width - PAD.left - PAD.right;
  const h = height - PAD.top - PAD.bottom;
  const sx = (t: number) => PAD.left + (t / xMax) * w;
  const sy = (v: number) => PAD.top + h - (v / max) * h;

  const path = (vals: (number | null)[], stepped: boolean) => {
    let d = '';
    let pen = false;
    vals.forEach((v, i) => {
      if (v == null) { pen = false; return; }
      if (!pen) { d += `M${sx(x[i])},${sy(v)}`; pen = true; return; }
      d += stepped ? `H${sx(x[i])}V${sy(v)}` : `L${sx(x[i])},${sy(v)}`;
    });
    return d;
  };

  const onMove = (e: React.PointerEvent<SVGRectElement>) => {
    if (!x.length) return;
    const r = e.currentTarget.getBoundingClientRect();
    const t = ((e.clientX - r.left) / r.width) * xMax;
    let best = 0;
    x.forEach((v, i) => { if (Math.abs(v - t) < Math.abs(x[best] - t)) best = i; });
    setHover(best);
  };

  const ticks: number[] = [];
  for (let v = 0; v <= max + 1e-9; v += step) ticks.push(v);
  const xTicks = 5;
  const xTickValues = xFormat
    ? [...new Set(Array.from({ length: xTicks + 1 }, (_, i) => Math.round((i * xMax) / xTicks)))]
    : Array.from({ length: xTicks + 1 }, (_, i) => (i * xMax) / xTicks);

  return (
    <figure className="lt-chart">
      <figcaption>
        <strong>{title}</strong>
        {series.length > 1 && (
          <span className="lt-legend">
            {series.map((s) => (
              <span key={s.name}><i className={s.muted ? 'muted' : ''} style={s.muted ? undefined : { background: s.color }} />{s.name}</span>
            ))}
          </span>
        )}
      </figcaption>
      <div ref={wrap} className="lt-plot">
        {!x.length ? (
          <div className="lt-empty" style={{ height }}>{empty}</div>
        ) : (
          <svg width={width} height={height} role="img" aria-label={title}>
            {ticks.map((v) => (
              <g key={v}>
                <line x1={PAD.left} x2={PAD.left + w} y1={sy(v)} y2={sy(v)} className="grid" />
                <text x={PAD.left - 8} y={sy(v)} className="ytick">{fmt(v)}</text>
              </g>
            ))}
            {xTickValues.map((t) => (
              <text key={t} x={sx(t)} y={height - 8} className="xtick">{fx(t)}</text>
            ))}
            {threshold && (
              <g>
                <line x1={PAD.left} x2={PAD.left + w} y1={sy(threshold.value)} y2={sy(threshold.value)} className="thr" />
                <text x={PAD.left + w} y={sy(threshold.value) - 5} className="thr-label">{threshold.label}</text>
              </g>
            )}
            {series.map((s) => (
              <path key={s.name} d={path(s.values, !!s.muted)} className={s.muted ? 'ref' : 'line'} style={s.muted ? undefined : { stroke: s.color }} />
            ))}
            {hover != null && (
              <g>
                <line x1={sx(x[hover])} x2={sx(x[hover])} y1={PAD.top} y2={PAD.top + h} className="cross" />
                {named.map((s) => s.values[hover] != null && (
                  <circle key={s.name} cx={sx(x[hover])} cy={sy(s.values[hover]!)} r={4} style={{ fill: s.color }} className="dot" />
                ))}
              </g>
            )}
            <rect x={PAD.left} y={PAD.top} width={w} height={h} fill="transparent"
              onPointerMove={onMove} onPointerLeave={() => setHover(null)} />
          </svg>
        )}
        {hover != null && x.length > 0 && (
          <div className="lt-tip" style={{ left: Math.min(sx(x[hover]) + 12, width - 170), top: PAD.top }}>
            <small>{fx(x[hover])}</small>
            {series.map((s) => (
              <div key={s.name}>
                <i className={s.muted ? 'muted' : ''} style={s.muted ? undefined : { background: s.color }} />
                <b>{s.values[hover] == null ? '—' : `${fmt(s.values[hover]!)} ${s.unit}`}</b>
                <span>{s.name}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </figure>
  );
}
