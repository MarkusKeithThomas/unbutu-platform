package com.unbutu.ticket.events.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Topic {@value #TOPIC} – payment-service phát (Outbox, sau khi IPN đã verify), key = bookingId
 * → mọi kết quả của 1 đơn cùng partition, đúng thứ tự. booking-service nghe để chốt / huỷ / hoàn tiền.
 */
public final class PaymentEvents {

    public static final String TOPIC = "payment.result.v1";

    public static final String SUCCEEDED = "payment.succeeded";
    public static final String FAILED = "payment.failed";
    public static final String REFUNDED = "payment.refunded";

    private PaymentEvents() {
    }

    /** amount: VND. providerTxnNo: mã giao dịch phía cổng (vnp_TransactionNo). */
    public record PaymentResult(UUID paymentId, UUID bookingId, String status, long amount, String method,
                                String providerTxnNo, String providerResponseCode, Instant at) {
    }
}
