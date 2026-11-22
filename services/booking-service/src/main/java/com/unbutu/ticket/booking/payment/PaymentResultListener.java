package com.unbutu.ticket.booking.payment;

import com.unbutu.ticket.events.payment.PaymentEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Nghe payment.result.v1 (key = bookingId → kết quả của 1 đơn đúng thứ tự). Lỗi tạm → retry vô hạn có backoff. */
@Component
@RequiredArgsConstructor
class PaymentResultListener {

    private final JsonMapper json;
    private final SettlementService settlement;

    // concurrency: N luồng chia 6 partition (key = bookingId → kết quả của 1 đơn vẫn tuần tự). 1 luồng ≈ 10 đơn/s trên prod
    // (load test 2026-11-22: docs/loadtest/2026-11-22-purchase-prod.md). Spring tự thêm "-0..N-1" vào group.instance.id.
    @KafkaListener(topics = PaymentEvents.TOPIC, concurrency = "${booking.kafka.payment-concurrency}",
            properties = "group.instance.id=${booking.kafka.instance-id}-payment")
    void onMessage(String value) {
        JsonNode envelope = json.readTree(value);
        settlement.onResult(envelope.path("type").asString(),
                json.treeToValue(envelope.path("payload"), PaymentEvents.PaymentResult.class));
    }
}
