package com.unbutu.ticket.catalog.outbox;

import com.unbutu.ticket.events.catalog.CatalogEvents;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Topic do producer khai báo (broker tắt auto-create → gõ nhầm tên topic là lỗi ngay, không lặng lẽ tạo topic mới).
 * Số partition = mức song song tối đa của consumer group; catalog ghi ít nên 6 là đủ, tăng được về sau (không giảm được).
 */
@Configuration
@EnableScheduling
class KafkaTopicsConfig {

    @Bean
    NewTopic catalogEventTopic(@Value("${catalog.kafka.partitions:6}") int partitions,
                               @Value("${catalog.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(CatalogEvents.TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                // Giữ vĩnh viễn (catalog rất ít dữ liệu): consumer mới (vd. inventory lần đầu) replay từ đầu để dựng trạng thái.
                // KHÔNG compact: key = id sự kiện, compact sẽ chỉ giữ message cuối của mỗi sự kiện → mất các hạng vé khác.
                .config("retention.ms", "-1")
                .config("cleanup.policy", "delete")
                .build();
    }
}
