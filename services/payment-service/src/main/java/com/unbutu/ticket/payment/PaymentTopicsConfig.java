package com.unbutu.ticket.payment;

import com.unbutu.ticket.events.payment.PaymentEvents;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** payment.result.v1: key = bookingId. Giữ 30 ngày (đủ để replay khi consumer lỗi lâu / điều tra). */
@Configuration
class PaymentTopicsConfig {

    @Bean
    NewTopic paymentResultTopic(@Value("${payment.kafka.partitions:6}") int partitions,
                                @Value("${payment.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(PaymentEvents.TOPIC).partitions(partitions).replicas(replicas)
                .config("retention.ms", Long.toString(30L * 24 * 3600 * 1000))
                .build();
    }
}
