package com.unbutu.ticket.events.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Topic {@value #TOPIC} – booking-service phát (Outbox, cùng transaction với đổi trạng thái đơn), key = bookingId.
 * ticketing (phát vé QR) và notification (email) nghe từ đây.
 *
 * <p>Event "béo": mang đủ người mua, sự kiện, từng hạng vé → consumer KHÔNG phải gọi ngược booking
 * (50k rps: 1 đơn xác nhận không sinh thêm N request đồng bộ; booking chậm/sập vẫn phát vé, gửi email được).
 */
public final class BookingEvents {

    public static final String TOPIC = "booking.event.v1";

    public static final String CONFIRMED = "booking.confirmed";

    private BookingEvents() {
    }

    /** Đã thu tiền + đã chốt vé ở inventory. Phát đúng 1 lần cho mỗi đơn (chỉ khi UPDATE … → CONFIRMED thành công). */
    public record BookingConfirmed(UUID bookingId, String code, UUID userId,
                                   UUID eventId, String eventTitle, String venue, Instant startsAt,
                                   String buyerName, String buyerEmail, String buyerPhone,
                                   long total, String paymentMethod, List<Item> items, Instant confirmedAt) {
    }

    /** quantity vé của 1 hạng → ticketing sinh quantity mã QR. unitPrice: VND, chụp lúc đặt. */
    public record Item(UUID ticketTypeId, String name, long unitPrice, int quantity) {
    }
}
