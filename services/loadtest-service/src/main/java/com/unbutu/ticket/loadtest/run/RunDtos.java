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
            boolean stopOnFail) {
    }

    public record Scenario(String name, String title, String description, boolean writes) {
    }

    /** Một điểm trên biểu đồ (2 s/lần). p95/p99/lỗi: tính dồn từ đầu bậc đang chạy. */
    public record Sample(double t, double rps, int vus, int stepRps, Double p95, Double p99, double errPct) {
    }

    /** Số liệu từng bậc. Trong lúc chạy: ước tính trực tiếp; xong: thay bằng bảng của k6 (result). */
    public record StepView(int rps, String state, long reqs, Double achievedRps, Double p95, Double p99, Double errPct,
                           long dropped, Boolean pass) {
    }

    public record RunSummary(UUID id, String scenario, List<Integer> steps, int stepSeconds, int p95Ms, double maxErrPct,
                             boolean stopOnFail, String status, String startedBy, Instant startedAt, Instant finishedAt,
                             Integer passUpToRps) {
    }

    public record RunDetail(RunSummary run, List<StepView> stepViews, List<Sample> samples, JsonNode result, String logTail) {
    }

    public record Limits(int maxRps, long maxDurationSeconds) {
    }
}
