package com.unbutu.ticket.booking;

import com.unbutu.ticket.events.booking.BookingEvents;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** booking.event.v1: key = bookingId. Giữ 30 ngày (ticketing/notification lỗi lâu vẫn replay được). */
@Configuration
class BookingTopicsConfig {

    @Bean
    NewTopic bookingEventTopic(@Value("${booking.kafka.partitions:6}") int partitions,
                               @Value("${booking.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(BookingEvents.TOPIC).partitions(partitions).replicas(replicas)
                .config("retention.ms", Long.toString(30L * 24 * 3600 * 1000))
                .build();
    }
}
