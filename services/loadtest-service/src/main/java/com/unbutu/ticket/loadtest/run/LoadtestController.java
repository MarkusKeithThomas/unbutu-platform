package com.unbutu.ticket.loadtest.run;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API cho trang /admin/loadtest (chỉ ADMIN – SecurityConfig). UI hỏi lại {@code /runs/current} mỗi 2 s khi đang chạy. */
@RestController
@RequestMapping("/api/loadtest")
@RequiredArgsConstructor
class LoadtestController {

    private final K6Runner runner;

    /** Kịch bản + giới hạn an toàn (UI dùng để dựng form). */
    @GetMapping("/config")
    Map<String, Object> config() {
        return Map.of("scenarios", K6Scripts.SCENARIOS, "limits", runner.limits());
    }

    @PostMapping("/runs")
    ResponseEntity<RunDtos.RunSummary> start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RunDtos.RunRequest req) {
        String who = jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : jwt.getSubject();
        // Token của ADMIN đang bấm: dùng ngay để chuẩn bị dữ liệu mua vé (tạo sự kiện ẩn, bật cờ) – không lưu lại DB.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(runner.start(req, who, jwt.getTokenValue()));
    }

    /** Lượt đang chạy (số liệu trực tiếp); 204 nếu không có. */
    @GetMapping("/runs/current")
    ResponseEntity<RunDtos.RunDetail> current() {
        return runner.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/runs")
    List<RunDtos.RunSummary> recent() {
        return runner.recent();
    }

    @GetMapping("/runs/{id}")
    RunDtos.RunDetail detail(@PathVariable UUID id) {
        return runner.detail(id);
    }

    @PostMapping("/runs/{id}/stop")
    ResponseEntity<Void> stop(@PathVariable UUID id) {
        runner.stop(id);
        return ResponseEntity.accepted().build();
    }
}
