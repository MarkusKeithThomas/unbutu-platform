package com.unbutu.ticket.payment.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tra cứu thanh toán cho admin (ADM-3) – NỘI BỘ: gateway không route /internal/**. booking-service (đã kiểm role ADMIN)
 * gọi và trả lại cho UI qua /api/bookings/admin/payments → payment-service không cần thêm lớp xác thực JWT.
 */
@Validated
@RestController
@RequiredArgsConstructor
class AdminPaymentController {

    private static final Duration MAX_RANGE = Duration.ofDays(367);

    private final AdminPaymentQueries queries;

    /** ?status=FAILED|PENDING|SUCCEEDED|REFUNDED &stuck=true (PENDING quá 15′) &bookingId= &q=<mã đơn|mã GD> &cursor= &size= */
    @GetMapping("/internal/admin/payments")
    AdminPaymentQueries.Page list(@RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "false") boolean stuck,
                                  @RequestParam(required = false) UUID bookingId,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (status != null && !AdminPaymentQueries.STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status không hợp lệ");
        }
        Cursor c;
        try {
            c = Cursor.decode(cursor);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor không hợp lệ");
        }
        return queries.list(status, stuck, bookingId, q, c, size);
    }

    /** Tổng hợp theo trạng thái + mã lỗi cổng (FAILED) trong [from, to) + số giao dịch treo hiện tại. */
    @GetMapping("/internal/admin/payments/summary")
    AdminPaymentQueries.Summary summary(@RequestParam Instant from, @RequestParam Instant to) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "khoảng thời gian không hợp lệ");
        }
        return queries.summary(from, to);
    }
}
