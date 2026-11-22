package com.unbutu.ticket.loadtest.run;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * Chạy MỘT lượt k6 tại một thời điểm (tiến trình con) và theo dõi nó:
 * <ul>
 *   <li>Mỗi {@code pollInterval} đọc {@code GET /v1/metrics} của k6 (REST API, 127.0.0.1): tổng request → rps thực,
 *       submetric theo bậc {@code http_req_duration{scenario:bac_..}} → p95/p99/lỗi/bỏ lượt từng bậc.</li>
 *   <li>Dừng: {@code PATCH /v1/status stopped=true} (k6 vẫn in bảng tổng kết), không được thì SIGTERM.</li>
 *   <li>Kết thúc: đọc bảng bậc k6 ghi ra ({@code handleSummary} trong lib/steps.js) → lưu Postgres cùng chuỗi điểm.</li>
 * </ul>
 * Trạng thái lượt đang chạy chỉ nằm trong bộ nhớ – service chạy đúng 1 instance (công cụ quản trị, không phải đường tải
 * của khách). Service khởi động lại giữa chừng → lượt đó thành ABORTED.
 */
@Slf4j
@Service
class K6Runner {

    private static final int DROP_PCT = 5;          // khớp DROP_PCT trong lib/steps.js
    private static final int LOG_TAIL_CHARS = 8000;
    private static final JsonNode MISSING = MissingNode.getInstance();

    private final LoadtestProperties props;
    private final K6Scripts scripts;
    private final PurchaseFixture purchase;
    private final RunRepository runs;
    private final JsonMapper json;
    private final Clock clock;
    private final RestClient k6Api;
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "k6-poller");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<Active> active = new AtomicReference<>();

    K6Runner(LoadtestProperties props, K6Scripts scripts, PurchaseFixture purchase, RunRepository runs, JsonMapper json,
             Clock clock, RestClient.Builder http) {
        this.props = props;
        this.scripts = scripts;
        this.purchase = purchase;
        this.runs = runs;
        this.json = json;
        this.clock = clock;
        this.k6Api = http.baseUrl("http://127.0.0.1:" + props.apiPort()).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    void abortDangling() {
        int n = runs.abortDangling(clock.instant());
        if (n > 0) {
            log.warn("Đánh dấu {} lượt load test RUNNING còn sót (service khởi động lại) → ABORTED", n);
        }
    }

    private Duration maxDuration(String scenario) {
        RunDtos.Scenario sc = K6Scripts.scenario(scenario);
        Duration own = sc == null ? props.maxDuration() : Duration.ofSeconds(sc.maxDurationSeconds());
        return own.compareTo(props.maxDuration()) < 0 ? own : props.maxDuration();
    }

    RunDtos.Limits limits() {
        return new RunDtos.Limits(props.maxRps(), props.maxDuration().toSeconds());
    }

    // ------------------------------------------------------------------ bắt đầu / dừng

    RunDtos.RunSummary start(RunDtos.RunRequest req, String startedBy, String adminToken) {
        Path script = scripts.script(req.scenario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không có kịch bản " + req.scenario()));
        List<Integer> steps = req.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i) > props.maxRps()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Bậc " + steps.get(i) + " rps vượt trần " + props.maxRps() + " rps");
            }
            if (i > 0 && steps.get(i) <= steps.get(i - 1)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Các bậc rps phải tăng dần, không trùng");
            }
        }
        Duration total = Duration.ofSeconds((long) steps.size() * req.stepSeconds());
        Duration max = maxDuration(req.scenario());
        if (total.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tổng thời gian " + total.toSeconds() / 60.0 + " phút vượt tối đa " + max.toMinutes() + " phút của kịch bản");
        }

        // Giữ chỗ "1 lượt/lúc" TRƯỚC khi tạo dữ liệu (2 người bấm cùng lúc không tạo 2 sự kiện).
        Active a = new Active(null, null);
        if (!active.compareAndSet(null, a)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đang có 1 lượt load test chạy – dừng nó trước");
        }
        PurchaseFixture.Fixture fixture;
        try {
            fixture = "purchase".equals(req.scenario()) ? purchase.prepare(adminToken, total) : null;
        } catch (RuntimeException e) {
            active.compareAndSet(a, null);
            throw e;
        }
        Instant now = clock.instant();
        var summary = new RunDtos.RunSummary(UUID.randomUUID(), req.scenario(), List.copyOf(steps), req.stepSeconds(),
                req.p95Ms(), req.maxErrPct(), req.stopOnFail(), "RUNNING", startedBy, now, null, null,
                fixture == null ? null : fixture.eventId());
        a.init(summary, props.workDir().resolve("runs").resolve(summary.id().toString()), fixture, adminToken);
        try {
            runs.insert(summary);
            Files.createDirectories(a.dir);
            ProcessBuilder pb = new ProcessBuilder(command(script))
                    .directory(scripts.dir().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(a.dir.resolve("k6.log").toFile());
            pb.environment().putAll(env(req, a.dir));
            if (fixture != null) {
                pb.environment().putAll(fixture.env());
            }
            a.process = pb.start();
        } catch (IOException | RuntimeException e) {
            log.error("Không chạy được k6 cho lượt {}", summary.id(), e);
            finish(a, "ERROR", "Không chạy được k6: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không chạy được k6: " + e.getMessage());
        }
        log.info("Bắt đầu load test {} kịch bản={} bậc={} mỗi bậc={}s bởi {}", summary.id(), req.scenario(), steps,
                req.stepSeconds(), startedBy);
        long every = props.pollInterval().toMillis();
        a.poll = poller.scheduleAtFixedRate(() -> poll(a, total), every, every, TimeUnit.MILLISECONDS);
        a.process.onExit().thenRun(() -> finish(a, null, null));
        return summary;
    }

    private List<String> command(Path script) {
        return List.of(props.k6Binary(), "run", "--no-color", "--quiet", "--address", "127.0.0.1:" + props.apiPort(),
                script.toString());
    }

    /** Biến môi trường cho kịch bản (đọc trong lib/steps.js). Giá trị đã được validate kiểu số ở RunRequest. */
    private Map<String, String> env(RunDtos.RunRequest req, Path dir) {
        Map<String, String> env = new HashMap<>();
        env.put("SCENARIO", req.scenario());
        env.put("STEPS", String.join(",", req.steps().stream().map(String::valueOf).toList()));
        env.put("STEP_TIME", req.stepSeconds() + "s");
        env.put("P95_MS", String.valueOf(req.p95Ms()));
        env.put("MAX_ERR_PCT", String.valueOf(req.maxErrPct()));
        env.put("STOP_ON_FAIL", String.valueOf(req.stopOnFail()));
        env.put("MAX_RPS", String.valueOf(props.maxRps()));
        env.put("BASE_URL", props.targetBaseUrl());
        env.put("OUT_DIR", dir.toString());
        env.put("OUT_NAME", "result");
        env.put("HOME", dir.toString());            // user app không có home – k6 cần chỗ ghi cấu hình
        env.put("K6_NO_USAGE_REPORT", "true");      // không gửi thống kê sử dụng ra Internet
        return env;
    }

    void stop(UUID id) {
        Active a = active.get();
        if (a == null || a.summary == null || !a.summary.id().equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lượt này không còn chạy");
        }
        a.stopRequested = true;
        try {
            k6Api.patch().uri("/v1/status").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("data", Map.of("type", "status", "id", "default", "attributes", Map.of("stopped", true))))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Không dừng được k6 qua REST API ({}), gửi SIGTERM", e.getMessage());
            a.process.destroy();
        }
    }

    @PreDestroy
    void shutdown() {
        Active a = active.get();
        if (a != null && a.process != null) {
            log.warn("Service tắt khi lượt {} đang chạy → dừng k6", a.summary.id());
            a.stopRequested = true;
            a.process.destroy();
            try {
                a.process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        poller.shutdownNow();
    }

    // ------------------------------------------------------------------ theo dõi trực tiếp

    private void poll(Active a, Duration total) {
        Instant now = clock.instant();
        // Lưới an toàn: k6 treo (setup kẹt…) quá tổng thời gian + 2 phút → giết.
        if (now.isAfter(a.summary.startedAt().plus(total).plus(Duration.ofMinutes(2))) && a.process.isAlive()) {
            log.warn("Lượt {} quá thời gian dự kiến → buộc dừng k6", a.summary.id());
            a.stopRequested = true;
            a.process.destroyForcibly();
            return;
        }
        JsonNode body;
        try {
            // k6 trả Content-Type text/plain (dù body là JSON) → đọc chuỗi rồi tự parse.
            body = json.readTree(k6Api.get().uri("/v1/metrics").retrieve().body(String.class));
        } catch (RuntimeException e) {
            log.debug("Chưa đọc được metrics k6: {}", e.getMessage());
            return;   // k6 chưa mở REST API (đang khởi động / setup) hoặc vừa thoát
        }
        Map<String, JsonNode> m = new HashMap<>();
        body.path("data").forEach(n -> m.put(n.path("id").asString(), n.path("attributes").path("sample")));

        List<Integer> steps = a.summary.steps();
        long total0 = m.getOrDefault("iterations", MISSING).path("count").asLong(0);
        long http0 = m.getOrDefault("http_reqs", MISSING).path("count").asLong(0);
        int current = -1;
        for (int i = 0; i < steps.size(); i++) {
            long c = metric(m, "iterations", i, steps).path("count").asLong(0);
            if (c > 0 && a.firstSeen[i] == null) {
                a.firstSeen[i] = now;
            }
            if (c > a.lastCount[i]) {
                current = i;
                a.lastSeen[i] = now;
            }
            a.lastCount[i] = c;
        }
        List<RunDtos.StepView> views = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            views.add(stepView(a, m, i, current));
        }
        a.steps = views;

        double dt = a.lastPollAt == null ? 0 : Duration.between(a.lastPollAt, now).toMillis() / 1000.0;
        double rps = dt > 0 ? Math.max(0, total0 - a.lastTotal) / dt : 0;
        double httpRps = dt > 0 ? Math.max(0, http0 - a.lastHttp) / dt : 0;
        a.lastPollAt = now;
        a.lastTotal = total0;
        a.lastHttp = http0;
        if (current >= 0) {
            RunDtos.StepView s = views.get(current);
            a.samples.add(new RunDtos.Sample(round1(Duration.between(a.summary.startedAt(), now).toMillis() / 1000.0),
                    round1(rps), round1(httpRps), m.getOrDefault("vus", MISSING).path("value").asInt(0), s.rps(), s.p95(),
                    s.p99(), s.errPct() == null ? 0 : s.errPct(), s.ttqP95()));
        }
    }

    private RunDtos.StepView stepView(Active a, Map<String, JsonNode> m, int i, int current) {
        List<Integer> steps = a.summary.steps();
        int rps = steps.get(i);
        long reqs = a.lastCount[i];
        if (reqs == 0) {
            return RunDtos.StepView.pending(rps, "PENDING");
        }
        JsonNode dur = metric(m, "http_req_duration", i, steps);
        Double p95 = num(dur, "p(95)");
        Double p99 = num(dur, "p(99)");
        double errPct = metric(m, "http_req_failed", i, steps).path("rate").asDouble(0) * 100;
        long dropped = metric(m, "dropped_iterations", i, steps).path("count").asLong(0);
        Double ttq = num(metric(m, "time_to_ticket", i, steps), "p(95)");
        JsonNode issued = metric(m, "ticket_issued", i, steps).path("rate");
        Double ticketPct = issued.isNumber() ? round1(issued.asDouble() * 100) : null;
        double secs = Math.max(1, Duration.between(a.firstSeen[i], a.lastSeen[i] == null ? a.firstSeen[i] : a.lastSeen[i])
                .toMillis() / 1000.0);
        boolean running = i == current;
        // Ước tính trực tiếp (tiêu chí chung); tiêu chí riêng của kịch bản có trong bảng chính thức lúc kết thúc.
        Boolean pass = running ? null
                : (p95 != null && p95 < a.summary.p95Ms()) && errPct < a.summary.maxErrPct()
                        && dropped < Math.max(1, Math.ceil(rps * a.summary.stepSeconds() * DROP_PCT / 100.0))
                        && (ticketPct == null || ticketPct > 99);
        return new RunDtos.StepView(rps, running ? "RUNNING" : "DONE", reqs, round1(reqs / secs), p95, p99,
                round2(errPct), dropped, pass, ttq, ticketPct);
    }

    /** Submetric theo bậc: tên scenario khớp stepName() trong lib/steps.js. */
    private JsonNode metric(Map<String, JsonNode> m, String name, int i, List<Integer> steps) {
        String scenario = "bac_%02d_%drps".formatted(i + 1, steps.get(i));
        return m.getOrDefault(name + "{scenario:" + scenario + "}", MISSING);
    }

    // ------------------------------------------------------------------ kết thúc

    /** Gọi khi tiến trình k6 thoát (hoặc không start được). Đọc bảng kết quả, lưu DB, nhả khoá "1 lượt/lúc". */
    private synchronized void finish(Active a, String forcedStatus, String error) {
        if (a.finished) {
            return;
        }
        a.finished = true;
        if (a.poll != null) {
            a.poll.cancel(false);
        }
        String resultJson = null;
        Integer passUpTo = null;
        try {
            Path result = a.dir.resolve("result.json");
            if (Files.isRegularFile(result)) {
                resultJson = Files.readString(result);
                JsonNode r = json.readTree(resultJson);
                passUpTo = r.path("passUpToRps").asInt(0);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Không đọc được kết quả k6 của lượt {}", a.summary.id(), e);
        }
        String status = forcedStatus != null ? forcedStatus
                : a.stopRequested ? "STOPPED"
                : resultJson != null ? "COMPLETED"
                : "ERROR";
        String logTail = tail(a.dir.resolve("k6.log"));
        if (error != null) {
            logTail = (logTail == null ? "" : logTail + "\n") + error;
        }
        try {
            runs.finish(a.summary.id(), status, clock.instant(), passUpTo, resultJson,
                    json.writeValueAsString(a.samples.snapshot()), logTail);
        } catch (RuntimeException e) {
            log.error("Không lưu được kết quả lượt {}", a.summary.id(), e);
        }
        if (a.fixture != null) {
            purchase.cleanup(a.adminToken, a.fixture);
        }
        log.info("Kết thúc load test {}: {} · PASS tới {} rps · exit={}", a.summary.id(), status, passUpTo,
                a.process == null || a.process.isAlive() ? null : a.process.exitValue());
        deleteQuietly(a.dir);
        active.compareAndSet(a, null);
    }

    // ------------------------------------------------------------------ đọc

    Optional<RunDtos.RunDetail> current() {
        Active a = active.get();
        return a == null || a.summary == null ? Optional.empty() : Optional.of(live(a));
    }

    RunDtos.RunDetail detail(UUID id) {
        Active a = active.get();
        if (a != null && a.summary != null && a.summary.id().equals(id)) {
            return live(a);
        }
        RunRepository.Stored s = runs.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có lượt " + id));
        JsonNode result = s.result() == null ? null : json.readTree(s.result());
        List<RunDtos.Sample> samples = s.samples() == null ? List.of()
                : List.of(json.readValue(s.samples(), RunDtos.Sample[].class));
        return new RunDtos.RunDetail(s.summary(), stepsFromResult(s.summary(), result), samples, result, s.logTail());
    }

    List<RunDtos.RunSummary> recent() {
        return runs.recent(30);
    }

    private RunDtos.RunDetail live(Active a) {
        List<RunDtos.StepView> steps = a.steps;
        if (steps == null) {
            steps = a.summary.steps().stream()
                    .map(r -> RunDtos.StepView.pending(r, "PENDING")).toList();
        }
        return new RunDtos.RunDetail(a.summary, steps, a.samples.snapshot(), null, tail(a.dir.resolve("k6.log")));
    }

    /** Bảng bậc chính thức (k6 handleSummary) → cùng dạng StepView với lúc đang chạy. */
    private static List<RunDtos.StepView> stepsFromResult(RunDtos.RunSummary run, JsonNode result) {
        if (result == null || !result.path("steps").isArray()) {
            return run.steps().stream().map(r -> RunDtos.StepView.pending(r, "SKIPPED")).toList();
        }
        List<RunDtos.StepView> out = new ArrayList<>();
        for (JsonNode s : result.path("steps")) {
            if (!s.path("ran").asBoolean(false)) {
                out.add(RunDtos.StepView.pending(s.path("rps").asInt(), "SKIPPED"));
                continue;
            }
            double achieved = s.path("achievedRps").asDouble(0);
            out.add(new RunDtos.StepView(s.path("rps").asInt(), "DONE",
                    Math.round(achieved * run.stepSeconds()), round1(achieved), num(s, "p95"), num(s, "p99"),
                    round2(s.path("errPct").asDouble(0)), s.path("dropped").asLong(0), s.path("pass").asBoolean(false),
                    num(s, "ttqP95"), num(s, "ticketPct")));
        }
        return out;
    }

    // ------------------------------------------------------------------ tiện ích

    private static Double num(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isNumber() ? round1(v.asDouble()) : null;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String tail(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            long start = Math.max(0, f.length() - LOG_TAIL_CHARS);
            f.seek(start);
            byte[] buf = new byte[(int) (f.length() - start)];
            f.readFully(buf);
            String s = new String(buf, StandardCharsets.UTF_8);
            return start > 0 && s.indexOf('\n') >= 0 ? s.substring(s.indexOf('\n') + 1) : s;
        } catch (IOException e) {
            return null;
        }
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // thư mục tạm – bỏ qua
        }
    }

    /** Trạng thái lượt đang chạy. Chỉ luồng k6-poller ghi các mảng đếm; luồng request chỉ đọc snapshot. */
    private static final class Active {
        volatile RunDtos.RunSummary summary;   // null = đang chuẩn bị dữ liệu (chưa start k6)
        Path dir;
        PurchaseFixture.Fixture fixture;
        String adminToken;
        long[] lastCount;
        Instant[] firstSeen;
        Instant[] lastSeen;
        final Samples samples = new Samples();
        volatile Process process;
        volatile ScheduledFuture<?> poll;
        volatile List<RunDtos.StepView> steps;
        volatile boolean stopRequested;
        boolean finished;
        Instant lastPollAt;
        long lastTotal;
        long lastHttp;

        Active(RunDtos.RunSummary summary, Path dir) {
            if (summary != null) {
                init(summary, dir, null, null);
            }
        }

        void init(RunDtos.RunSummary summary, Path dir, PurchaseFixture.Fixture fixture, String adminToken) {
            int n = summary.steps().size();
            this.dir = dir;
            this.fixture = fixture;
            this.adminToken = adminToken;
            this.lastCount = new long[n];
            this.firstSeen = new Instant[n];
            this.lastSeen = new Instant[n];
            this.summary = summary;
        }
    }

    /** Chuỗi điểm biểu đồ – tối đa 1 điểm / pollInterval (30 phút × 2 s = 900 điểm). */
    private static final class Samples {
        private final List<RunDtos.Sample> list = new ArrayList<>();

        synchronized void add(RunDtos.Sample s) {
            list.add(s);
        }

        synchronized List<RunDtos.Sample> snapshot() {
            return List.copyOf(list);
        }
    }
}
