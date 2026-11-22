package com.unbutu.ticket.loadtest.run;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class RunDtos {

    private RunDtos() {
    }

    /** Bấm "Chạy": kịch bản + các bậc rps tăng dần + tiêu chí PASS mỗi bậc (ý nghĩa: lib/steps.js). */
    public record RunRequest(
            @NotNull @Pattern(regexp = "[a-z0-9-]{1,40}") String scenario,
            @NotEmpty @Size(max = 12) List<@NotNull @Min(1) Integer> steps,
            @Min(10) @Max(600) int stepSeconds,
            @Min(10) @Max(10_000) int p95Ms,
            @DecimalMin("0") @DecimalMax("50") double maxErrPct,
            boolean stopOnFail,
            @Min(500) @Max(60_000) Integer qrP95Ms) {   // chỉ purchase: p95 "bấm mua → có QR" tối đa; null = 5000
        public int qrP95MsOrDefault() {
            return qrP95Ms == null ? 5000 : qrP95Ms;
        }
    }

    /** maxDurationSeconds: trần riêng của kịch bản (purchase: token tài khoản test sống 15′ → ≤ 14′). */
    public record Scenario(String name, String title, String description, boolean writes, String unit,
                           long maxDurationSeconds) {
    }

    /** Một điểm trên biểu đồ (2 s/lần). rps = lượt/giây; httpRps = request/giây. p95/lỗi/ttq: dồn từ đầu bậc đang chạy. */
    public record Sample(double t, double rps, double httpRps, int vus, int stepRps, Double p95, Double p99, double errPct,
                         Double ttqP95) {
    }

    /** Số liệu từng bậc. Trong lúc chạy: ước tính trực tiếp; xong: thay bằng bảng của k6 (result). */
    /** ttqP95 / ticketPct: chỉ kịch bản mua vé (thời gian bấm mua → có QR, % đơn có vé). */
    public record StepView(int rps, String state, long reqs, Double achievedRps, Double p95, Double p99, Double errPct,
                           long dropped, Boolean pass, Double ttqP95, Double ticketPct, List<String> failed) {
        static StepView pending(int rps, String state) {
            return new StepView(rps, state, 0, null, null, null, null, 0, null, null, null, List.of());
        }
    }

    public record RunSummary(UUID id, String scenario, List<Integer> steps, int stepSeconds, int p95Ms, double maxErrPct,
                             boolean stopOnFail, String status, String startedBy, Instant startedAt, Instant finishedAt,
                             Integer passUpToRps, UUID eventId, Integer qrP95Ms) {
    }

    public record RunDetail(RunSummary run, List<StepView> stepViews, List<Sample> samples, JsonNode result, String logTail) {
    }

    public record Limits(int maxRps, long maxDurationSeconds) {
    }
}
