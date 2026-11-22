package com.unbutu.ticket.booking.admin;

import com.unbutu.ticket.booking.payment.PaymentClient;
import com.unbutu.ticket.booking.web.BookingException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Tra cứu đơn (ADM-2) + thanh toán (ADM-3) cho admin – chỉ ADMIN (SecurityConfig: /api/bookings/admin/**).
 * Thanh toán thuộc payment-service: booking kiểm quyền rồi gọi /internal/admin/payments** (gateway không route /internal)
 * và trả nguyên JSON – payment-service không cần lớp JWT riêng.
 */
@Validated
@RestController
@RequiredArgsConstructor
class AdminBookingController {

    record Detail(AdminBookingQueries.BookingRow booking, List<AdminBookingQueries.Line> lines,
                  JsonNode payments, boolean paymentsUnavailable) {
    }

    private final AdminBookingQueries queries;
    private final PaymentClient payments;

    /** ?status= &eventId= &q=<ORD-…|email|SĐT|id đơn|id user> &from= &to= (ngày VN) &includeTest= &cursor= &size= */
    @GetMapping("/api/bookings/admin/bookings")
    AdminBookingQueries.Page list(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) UUID eventId,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "false") boolean includeTest,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (status != null && !AdminBookingQueries.STATUSES.contains(status)) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Trạng thái không hợp lệ");
        }
        return queries.list(status, eventId, q,
                from == null ? null : from.atStartOfDay(AdminStatsService.VN).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(AdminStatsService.VN).toInstant(),
                includeTest, cursor(cursor), size);
    }

    /** Chi tiết đơn + các lần thanh toán. payment-service lỗi → vẫn trả đơn, paymentsUnavailable = true. */
    @GetMapping("/api/bookings/admin/bookings/{id}")
    Detail detail(@PathVariable UUID id) {
        var booking = queries.find(id).orElseThrow(BookingException::notFound);
        JsonNode pays = null;
        try {
            pays = payments.adminGet("/internal/admin/payments", Map.of("bookingId", id.toString(), "size", "50"));
        } catch (BookingException e) {
            // hiển thị đơn trước, thanh toán báo "tạm không tải được"
        }
        return new Detail(booking, queries.lines(id), pays == null ? null : pays.path("items"), pays == null);
    }

    /** ADM-3: danh sách thanh toán (status / stuck / q / bookingId / cursor / size) – chuyển tiếp payment-service. */
    @GetMapping("/api/bookings/admin/payments")
    JsonNode payments(@RequestParam Map<String, String> params) {
        Map<String, String> p = new HashMap<>();
        for (String k : List.of("status", "stuck", "bookingId", "q", "cursor", "size")) {
            if (params.get(k) != null && !params.get(k).isBlank()) {
                p.put(k, params.get(k));
            }
        }
        return payments.adminGet("/internal/admin/payments", p);
    }

    /** ADM-3: tổng hợp thanh toán theo ngày VN [from, to] (mặc định 30 ngày) + số giao dịch treo. */
    @GetMapping("/api/bookings/admin/payments/summary")
    JsonNode paymentSummary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now(AdminStatsService.VN);
        LocalDate f = from != null ? from : t.minusDays(29);
        return payments.adminGet("/internal/admin/payments/summary", Map.of(
                "from", f.atStartOfDay(AdminStatsService.VN).toInstant().toString(),
                "to", t.plusDays(1).atStartOfDay(AdminStatsService.VN).toInstant().toString()));
    }

    private static Cursor cursor(String s) {
        try {
            return Cursor.decode(s);
        } catch (RuntimeException e) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Con trỏ trang không hợp lệ");
        }
    }
}
