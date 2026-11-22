package com.unbutu.ticket.booking.loadtest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bật/tắt cờ load test theo sự kiện – chỉ ADMIN (SecurityConfig). */
@Validated
@RestController
@RequestMapping("/api/bookings/admin/limit-bypass")
@RequiredArgsConstructor
class LimitBypassController {

    private final LimitBypass bypass;

    @GetMapping
    List<LimitBypass.Entry> list() {
        return bypass.list();
    }

    /** PUT /{eventId}?minutes=30 – bật (hoặc gia hạn) trong N phút (≤ 120). */
    @PutMapping("/{eventId}")
    LimitBypass.Entry enable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId,
                             @RequestParam(defaultValue = "30") @Min(1) @Max(120) int minutes) {
        return bypass.enable(eventId, Duration.ofMinutes(minutes), who(jwt));
    }

    @DeleteMapping("/{eventId}")
    ResponseEntity<Void> disable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        bypass.disable(eventId, who(jwt));
        return ResponseEntity.noContent().build();
    }

    private static String who(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return email != null ? email : jwt.getSubject();
    }
}
