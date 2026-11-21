package com.unbutu.ticket.notification.ticket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

@Configuration
class KafkaConsumerConfig {

    /** Như booking: lỗi tạm (DB chết) → retry mãi (1 s → 30 s), không bỏ đơn nào. Chỉ message hỏng mới bị bỏ qua (log). */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        var backoff = new ExponentialBackOff(1_000, 2);
        backoff.setMaxInterval(30_000);
        var handler = new DefaultErrorHandler(backoff);
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class, NullPointerException.class);
        return handler;
    }
}
