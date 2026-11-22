package com.unbutu.ticket.booking.admin;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard admin – chỉ ADMIN (SecurityConfig: /api/bookings/admin/**). */
@RestController
@RequiredArgsConstructor
class AdminStatsController {

    private final AdminStatsService stats;

    /**
     * GET /api/bookings/admin/stats?from=2026-11-01&to=2026-11-22&eventId=&includeTest=false
     * – ngày theo giờ VN; mặc định 30 ngày gần nhất, bỏ sự kiện ẩn (load test). Cache 60 s.
     */
    @GetMapping("/api/bookings/admin/stats")
    AdminStats stats(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                     @RequestParam(required = false) UUID eventId,
                     @RequestParam(defaultValue = "false") boolean includeTest) {
        return stats.stats(from, to, eventId, includeTest);
    }
}
