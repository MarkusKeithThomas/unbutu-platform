package com.unbutu.ticket.booking.booking;

import com.unbutu.ticket.booking.booking.BookingDtos.BookingPage;
import com.unbutu.ticket.booking.booking.BookingDtos.BookingView;
import com.unbutu.ticket.booking.booking.BookingDtos.CreateBookingRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Đơn đặt vé của người đang đăng nhập (userId = sub của access token). Không cache (dữ liệu riêng từng người). */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
class BookingController {

    private final BookingService bookings;

    /**
     * A9 – giữ vé 10′. {@code Idempotency-Key} bắt buộc (UI sinh 1 lần / lần bấm): mạng chập chờn bấm lại / retry
     * → cùng đơn, không giữ vé 2 lần. 201 = đơn mới, 200 = trả lại đơn cũ.
     */
    @PostMapping
    ResponseEntity<BookingView> create(@AuthenticationPrincipal Jwt jwt,
                                       @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String key,
                                       @Valid @RequestBody CreateBookingRequest req) {
        var r = bookings.create(userId(jwt), key, req);
        return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK).body(r.booking());
    }

    /** A10 – sang cổng thanh toán. IP khách (X-Forwarded-For từ Funnel/gateway) gửi cho cổng như VNPay yêu cầu. */
    @PostMapping("/{id}/checkout")
    BookingDtos.CheckoutResponse checkout(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody BookingDtos.CheckoutRequest req, HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        String ip = xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : http.getRemoteAddr();
        return bookings.checkout(userId(jwt), id, req, ip);
    }

    /** A11 – UI poll (đếm ngược, trạng thái). */
    @GetMapping("/{id}")
    BookingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookings.get(userId(jwt), id);
    }

    /** A12 – lịch sử đơn, mới trước. */
    @GetMapping("/me")
    BookingPage mine(@AuthenticationPrincipal Jwt jwt,
                     @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
                     @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return bookings.mine(userId(jwt), page, size);
    }

    /** A13 – huỷ (nhả vé). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        bookings.cancel(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
