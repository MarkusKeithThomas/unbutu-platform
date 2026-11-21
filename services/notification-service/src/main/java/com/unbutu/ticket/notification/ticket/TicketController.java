package com.unbutu.ticket.notification.ticket;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
class TicketController {

    record TicketView(UUID id, UUID bookingId, String bookingCode, int seq, UUID eventId, String eventTitle, String venue,
                      Instant startsAt, UUID ticketTypeId, String ticketTypeName, long unitPrice, String status, String qr,
                      Instant issuedAt) {
        static TicketView of(TicketRepository.Row r) {
            return new TicketView(r.id(), r.bookingId(), r.bookingCode(), r.seq(), r.eventId(), r.eventTitle(), r.venue(),
                    r.startsAt(), r.ticketTypeId(), r.ticketTypeName(), r.unitPrice(), r.status(), r.qr(), r.issuedAt());
        }
    }

    record BookingTickets(UUID bookingId, List<TicketView> tickets) {
    }

    record TicketPage(List<TicketView> items, int page, int size, boolean hasNext) {
    }

    private final TicketRepository tickets;
    private final QrSigner qr;

    /**
     * Vé của 1 đơn. Rỗng = chưa phát (vé phát bất đồng bộ sau CONFIRMED, thường &lt; 1 s) → UI poll.
     * Đã có vé: bất biến → cache phía trình duyệt 5′ (private: chỉ chủ vé), bớt tải khi khách mở lại trang nhiều lần.
     */
    @GetMapping("/bookings/{bookingId}")
    ResponseEntity<BookingTickets> byBooking(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        var list = tickets.byBooking(bookingId, userId(jwt)).stream().map(TicketView::of).toList();
        var cache = list.isEmpty() ? CacheControl.noStore() : CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate();
        return ResponseEntity.ok().cacheControl(cache).body(new BookingTickets(bookingId, list));
    }

    /** Vé của tôi, mới trước (Slice, không COUNT). */
    @GetMapping("/me")
    TicketPage mine(@AuthenticationPrincipal Jwt jwt,
                    @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
                    @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        var rows = tickets.mine(userId(jwt), page, size);
        boolean hasNext = rows.size() > size;
        return new TicketPage(rows.stream().limit(size).map(TicketView::of).toList(), page, size, hasNext);
    }

    /** Public key verify QR (app soát vé tải và cache – verify offline). */
    @GetMapping("/.well-known/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic()).body(qr.publicJwks());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
