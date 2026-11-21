package com.unbutu.ticket.notification;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Thông báo + vé điện tử. Package {@code ticket} (phát vé QR, API vé) giữ độc lập với phần gửi thông báo (sau này: email)
 * → khi tải soát vé cần tách thành ticketing-service thì chuyển nguyên package (ADR-0009).
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
