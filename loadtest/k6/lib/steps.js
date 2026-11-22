// Chạy tải theo BẬC: mỗi bậc = 1 scenario constant-arrival-rate (giữ đúng N LƯỢT/giây trong STEP_TIME; browse: 1 lượt =
// 1 request; purchase: 1 lượt = 1 lần mua đủ luồng ≈ 6–8 request),
// các bậc nối tiếp nhau. Mỗi bậc có tiêu chí riêng → chấm PASS/FAIL từng bậc → "chịu được tới X rps".
//
// Biến môi trường (truyền qua `make loadtest …`):
//   STEPS        các bậc rps, tăng dần, vd. "50,100,200,400"      (mặc định 50,100,200)
//   STEP_TIME    thời gian mỗi bậc: "30s", "2m"                    (mặc định 30s)
//   P95_MS       p95 tối đa (ms) để bậc PASS                        (mặc định 300)
//   MAX_ERR_PCT  % lỗi tối đa (HTTP ≥ 400 hoặc lỗi mạng)            (mặc định 1)
//   STOP_ON_FAIL true = dừng ngay khi 1 bậc FAIL (đỡ dồn tải vào prod) (mặc định true)
//   MAX_RPS      trần an toàn: từ chối bậc lớn hơn                  (mặc định 2000)
// Bậc PASS khi: p95 < P95_MS, lỗi < MAX_ERR_PCT, k6 bắn đủ ≥ 95% số lượt (dropped_iterations < 5%)
// + mọi tiêu chí riêng của kịch bản (extraThresholds, vd. purchase: ≥ 99% đơn có vé QR).

const STEPS = (__ENV.STEPS || '50,100,200').split(',').map((s) => Number(s.trim()));
const STEP_TIME = __ENV.STEP_TIME || '30s';
const P95_MS = Number(__ENV.P95_MS || 300);
const MAX_ERR_PCT = Number(__ENV.MAX_ERR_PCT || 1);
const STOP_ON_FAIL = (__ENV.STOP_ON_FAIL || 'true') !== 'false';
const MAX_RPS = Number(__ENV.MAX_RPS || 2000);
const DROP_PCT = 5;

function seconds(d) {
  const m = /^(\d+)(s|m)$/.exec(d);
  if (!m) throw new Error(`STEP_TIME không hợp lệ: "${d}" (vd. 30s, 2m)`);
  return Number(m[1]) * (m[2] === 'm' ? 60 : 1);
}

const STEP_SEC = seconds(STEP_TIME);

function validate() {
  if (!STEPS.length || STEPS.some((n) => !Number.isInteger(n) || n <= 0)) {
    throw new Error(`STEPS phải là các số nguyên dương, vd. "50,100,200" (nhận: "${__ENV.STEPS}")`);
  }
  STEPS.forEach((n, i) => {
    if (i > 0 && n <= STEPS[i - 1]) throw new Error('STEPS phải tăng dần, không trùng');
    if (n > MAX_RPS) throw new Error(`Bậc ${n} rps vượt trần MAX_RPS=${MAX_RPS}`);
  });
}

export function stepName(i) {
  return `bac_${String(i + 1).padStart(2, '0')}_${STEPS[i]}rps`;
}

/**
 * options của k6: scenarios theo bậc + thresholds theo từng bậc (lọc theo tag scenario tự gắn).
 * @param iterSeconds     ước lượng thời gian 1 lượt (s) – để cấp đủ người dùng ảo (VU ≈ rps × thời gian lượt)
 * @param extraThresholds (sel) => { 'metric{scenario:..}': [...] } – tiêu chí riêng của kịch bản cho từng bậc
 */
export function buildOptions({ iterSeconds = 0.2, extraThresholds = () => ({}) } = {}) {
  validate();
  const scenarios = {};
  const thresholds = {};
  STEPS.forEach((rps, i) => {
    const name = stepName(i);
    scenarios[name] = {
      executor: 'constant-arrival-rate',
      rate: rps,
      timeUnit: '1s',
      duration: `${STEP_SEC}s`,
      startTime: `${i * STEP_SEC}s`,
      // VU = số request đang chờ cùng lúc ≈ rps × độ trễ. Hết VU → k6 bỏ lượt (dropped_iterations) → bậc FAIL.
      preAllocatedVUs: Math.min(Math.max(10, Math.ceil(rps * iterSeconds)), 1000),
      maxVUs: Math.min(Math.max(50, Math.ceil(rps * iterSeconds * 4)), 4000),
      gracefulStop: '5s',
    };
    const abort = { abortOnFail: STOP_ON_FAIL, delayAbortEval: '10s' };
    const sel = `{scenario:${name}}`;
    thresholds[`http_req_duration${sel}`] = [{ threshold: `p(95)<${P95_MS}`, ...abort }];
    thresholds[`http_req_failed${sel}`] = [{ threshold: `rate<${MAX_ERR_PCT / 100}`, ...abort }];
    thresholds[`dropped_iterations${sel}`] = [`count<${Math.max(1, Math.ceil(rps * STEP_SEC * DROP_PCT / 100))}`];
    thresholds[`http_reqs${sel}`] = ['count>=0']; // chỉ để summary/REST API có số request của bậc
    thresholds[`iterations${sel}`] = ['count>=0']; // … và số lượt (rps của bậc tính theo lượt)
    Object.assign(thresholds, extraThresholds(sel));
  });
  return {
    scenarios,
    thresholds,
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
    discardResponseBodies: true,
    userAgent: 'unbutu-loadtest/k6',
  };
}

const pad = (s, n) => String(s).padEnd(n);
const ms = (v) => (v === undefined ? '-' : `${Math.round(v)} ms`);

/** Bảng PASS/FAIL theo bậc (in ra màn hình) + JSON (OUT_DIR, mặc định /out, nếu có OUT_NAME). loadtest-service đọc file này. */
export function summary(data) {
  const rows = [];
  let passUpTo = 0;
  let broken = false;
  STEPS.forEach((rps, i) => {
    const sel = `{scenario:${stepName(i)}}`;
    const m = (k) => data.metrics[`${k}${sel}`];
    const reqs = m('http_reqs')?.values.count || 0;
    const iters = m('iterations')?.values.count || 0;
    if (!reqs) {
      rows.push({ rps, ran: false });
      return;
    }
    const dur = m('http_req_duration');
    const failed = m('http_req_failed');
    const dropped = m('dropped_iterations');
    // PASS = MỌI threshold gắn với bậc này đều đạt (gồm tiêu chí riêng của kịch bản).
    const ok = Object.entries(data.metrics)
      .filter(([k]) => k.endsWith(sel))
      .every(([, x]) => Object.values(x.thresholds || {}).every((t) => t.ok));
    const ttq = m('time_to_ticket');
    const issued = m('ticket_issued');
    const row = {
      rps,
      ran: true,
      pass: ok,
      achievedRps: iters / STEP_SEC,
      httpRps: reqs / STEP_SEC,
      ttqP95: ttq?.values['p(95)'],
      ticketPct: issued ? issued.values.rate * 100 : undefined,
      p95: dur?.values['p(95)'],
      p99: dur?.values['p(99)'],
      errPct: (failed?.values.rate || 0) * 100,
      dropped: dropped?.values.count || 0,
    };
    rows.push(row);
    if (ok && !broken) passUpTo = rps;
    if (!ok) broken = true;
  });

  const lines = [
    '',
    `Tiêu chí mỗi bậc: p95 < ${P95_MS} ms · lỗi < ${MAX_ERR_PCT}% · bỏ lượt < ${DROP_PCT}% · mỗi bậc ${STEP_SEC}s`,
    `${pad('Bậc', 10)}${pad('đạt rps', 10)}${pad('p95', 10)}${pad('p99', 10)}${pad('lỗi', 8)}${pad('bỏ lượt', 9)}kết quả`,
  ];
  for (const r of rows) {
    if (!r.ran) {
      lines.push(`${pad(`${r.rps} rps`, 10)}${pad('-', 47)}không chạy (đã dừng)`);
      continue;
    }
    lines.push(
      pad(`${r.rps} rps`, 10) + pad(r.achievedRps.toFixed(0), 10) + pad(ms(r.p95), 10) + pad(ms(r.p99), 10) +
        pad(`${r.errPct.toFixed(2)}%`, 8) + pad(r.dropped, 9) + (r.pass ? 'PASS' : 'FAIL') +
        (r.ttqP95 !== undefined ? `   · tới lúc có QR p95 ${ms(r.ttqP95)}, ${r.ticketPct?.toFixed(1)}% đơn có vé` : ''),
    );
  }
  const firstFail = rows.find((r) => r.ran && !r.pass);
  lines.push('');
  lines.push(
    passUpTo
      ? `KẾT QUẢ: PASS tới ${passUpTo} rps${firstFail ? ` (bậc ${firstFail.rps} rps FAIL)` : ' (mọi bậc đều PASS – thử bậc cao hơn)'}`
      : 'KẾT QUẢ: FAIL ngay bậc đầu tiên – hạ STEPS xuống',
  );
  lines.push('');

  const out = { stdout: lines.join('\n') };
  if (__ENV.OUT_NAME) {
    out[`${__ENV.OUT_DIR || '/out'}/${__ENV.OUT_NAME}.json`] = JSON.stringify(
      { scenario: __ENV.SCENARIO, steps: rows, passUpToRps: passUpTo, criteria: { p95Ms: P95_MS, maxErrPct: MAX_ERR_PCT, dropPct: DROP_PCT, stepSec: STEP_SEC } },
      null,
      2,
    );
  }
  return out;
}
