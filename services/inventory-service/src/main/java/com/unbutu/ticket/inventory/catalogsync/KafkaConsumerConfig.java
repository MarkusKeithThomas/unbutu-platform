package com.unbutu.ticket.inventory.catalogsync;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

@Configuration
class KafkaConsumerConfig {

    /**
     * Mặc định Spring thử 10 lần rồi BỎ message → mất cập nhật tồn kho khi Redis/DB chết lâu. Ở đây: retry mãi
     * (1 s → 30 s), partition dừng lại chờ hạ tầng hồi phục. Chỉ message hỏng (JSON sai) mới bị bỏ qua.
     * TODO khi có DLT: chuyển message hỏng sang catalog.event.v1.DLT thay vì chỉ log.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        var backoff = new ExponentialBackOff(1_000, 2);
        backoff.setMaxInterval(30_000);
        var handler = new DefaultErrorHandler(backoff);
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class, NullPointerException.class);
        return handler;
    }
}
