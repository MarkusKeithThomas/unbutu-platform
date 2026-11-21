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

    @KafkaListener(topics = PaymentEvents.TOPIC)
    void onMessage(String value) {
        JsonNode envelope = json.readTree(value);
        settlement.onResult(envelope.path("type").asString(),
                json.treeToValue(envelope.path("payload"), PaymentEvents.PaymentResult.class));
    }
}
