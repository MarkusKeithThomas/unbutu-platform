package com.unbutu.ticket.booking.payment;

import com.unbutu.ticket.booking.booking.BookingQueries;
import com.unbutu.ticket.booking.inventory.InventoryClient;
import com.unbutu.ticket.events.payment.PaymentEvents;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Quyết toán đơn theo kết quả thanh toán (payment.result.v1). Mọi bước idempotent (UPDATE … WHERE status = <cũ>,
 * inventory commit / payment refund idempotent) → event đến 2 lần, consumer retry giữa chừng đều an toàn.
 *
 * <pre>
 * SUCCEEDED → chốt vé ở inventory → CONFIRMED
 *          ↘ không còn đủ vé (trả muộn) / sai số tiền → REFUND_PENDING → hoàn tiền → (payment.refunded) REFUNDED
 * FAILED    → AWAITING_PAYMENT → PENDING (thanh toán lại nếu còn hạn)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final BookingQueries bookings;
    private final InventoryClient inventory;
    private final PaymentClient payment;

    public void onResult(String type, PaymentEvents.PaymentResult r) {
        switch (type) {
            case PaymentEvents.SUCCEEDED -> settlePaid(r);
            case PaymentEvents.FAILED -> bookings.backToPending(r.bookingId());
            case PaymentEvents.REFUNDED -> bookings.refunded(r.bookingId());
            default -> {
            }
        }
    }

    private void settlePaid(PaymentEvents.PaymentResult r) {
        var b = bookings.snapshot(r.bookingId()).orElse(null);
        if (b == null) {
            log.error("payment.succeeded cho đơn không tồn tại {} – hoàn tiền", r.bookingId());
            payment.refund(r.bookingId());
            return;
        }
        switch (b.status()) {
            case "CONFIRMED", "REFUNDED" -> {
                return;                                     // đã xử lý (event lặp)
            }
            case "REFUND_PENDING" -> {
                payment.refund(b.id());                    // lần trước hoàn tiền lỗi → thử lại
                return;
            }
            default -> {
            }
        }
        if (r.amount() != b.total()) {
            log.error("Đơn {} thu {} đ nhưng tổng {} đ – hoàn tiền", b.code(), r.amount(), b.total());
            refund(b.id(), "AMOUNT_MISMATCH");
            return;
        }
        // Lỗi mạng tới inventory → exception → consumer retry (DefaultErrorHandler backoff), không mất tiền của khách.
        if (inventory.commit(b.eventId(), b.id(), b.items())) {
            bookings.confirm(b.id());
            log.info("Đơn {} CONFIRMED ({} đ, {} vé)", b.code(), b.total(), b.ticketCount());
        } else {
            log.warn("Đơn {} đã trả tiền nhưng không còn đủ vé (trả muộn) – hoàn tiền", b.code());
            refund(b.id(), "SOLD_OUT_AFTER_PAYMENT");
        }
    }

    private void refund(UUID bookingId, String reason) {
        bookings.refundPending(bookingId, reason);
        payment.refund(bookingId);                         // payment.refunded → REFUNDED
    }
}
