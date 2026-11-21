package com.unbutu.ticket.booking.booking;

import com.unbutu.ticket.booking.inventory.InventoryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Đơn PENDING quá hạn → EXPIRED (1 câu UPDATE theo lô, SKIP LOCKED → chạy nhiều instance song song an toàn),
 * rồi nhả vé best-effort. Nhả lỗi cũng không sao: sweeper của inventory trả vé khi hold hết hạn (lớp dự phòng).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BookingExpiryJob {

    private final BookingRepository bookings;
    private final InventoryClient inventory;

    @Scheduled(fixedDelayString = "${booking.expiry-interval:5s}")
    void expire() {
        var expired = bookings.expireDue(500);
        expired.forEach(e -> inventory.release(e.eventId(), e.id()));
        if (!expired.isEmpty()) {
            log.info("Hết hạn giữ vé: {} đơn → EXPIRED", expired.size());
        }
    }
}
