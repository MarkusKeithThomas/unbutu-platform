package com.unbutu.ticket.outbox;

import java.time.Clock;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/** Bật khi service có JDBC + Kafka trên classpath (tự tạo bảng outbox bằng Flyway – README). */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"})
@ConditionalOnClass({JdbcClient.class, KafkaTemplate.class})
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcClient.class)
    Outbox outbox(JdbcClient jdbc, JsonMapper json, ObjectProvider<Clock> clock) {
        return new Outbox(jdbc, json, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({JdbcClient.class, KafkaTemplate.class})
    @ConditionalOnProperty(name = "outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
    OutboxRelay outboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, KafkaAdmin admin,
                            ObjectProvider<NewTopic> topics, ObjectProvider<Clock> clock,
                            @Value("${spring.application.name}") String serviceName) {
        List<NewTopic> declared = topics.orderedStream().toList();
        return new OutboxRelay(jdbc, kafka, admin, declared, clock.getIfAvailable(Clock::systemUTC), serviceName);
    }
}
