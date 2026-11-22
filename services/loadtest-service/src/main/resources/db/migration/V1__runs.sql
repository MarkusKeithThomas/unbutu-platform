-- Lịch sử các lượt load test (mỗi lượt = 1 lần bấm "Chạy"). Ít dòng (vài lượt/ngày) → không cần phân vùng.
CREATE TABLE runs (
    id             UUID PRIMARY KEY,
    scenario       VARCHAR(40)  NOT NULL,
    steps          VARCHAR(200) NOT NULL,          -- các bậc rps, vd. "50,100,200"
    step_seconds   INT          NOT NULL,
    p95_ms         INT          NOT NULL,
    max_err_pct    NUMERIC(5, 2) NOT NULL,
    stop_on_fail   BOOLEAN      NOT NULL,
    status         VARCHAR(20)  NOT NULL,          -- RUNNING | COMPLETED | STOPPED | ERROR | ABORTED
    started_by     VARCHAR(320) NOT NULL,          -- email ADMIN bấm chạy
    started_at     TIMESTAMPTZ  NOT NULL,
    finished_at    TIMESTAMPTZ,
    pass_up_to_rps INT,                            -- "chịu được tới X rps" (bậc cao nhất PASS liên tiếp), NULL = chưa có
    result         JSONB,                          -- bảng bậc do k6 handleSummary ghi (lib/steps.js)
    samples        JSONB,                          -- chuỗi điểm 2 s/lần cho biểu đồ (rps, VU, p95, lỗi)
    log_tail       TEXT                            -- cuối log k6 (lỗi setup, kịch bản…)
);
CREATE INDEX ix_runs_started_at ON runs (started_at DESC);
