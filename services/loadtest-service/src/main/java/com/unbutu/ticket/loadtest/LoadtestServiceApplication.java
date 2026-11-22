package com.unbutu.ticket.loadtest;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * Nút bấm load test trên web (LT-C): chạy k6 (binary có sẵn trong image) như tiến trình con, mỗi lúc 1 lượt, có trần rps,
 * đọc số liệu trực tiếp qua REST API của k6 và lưu lịch sử vào Postgres. Chỉ ADMIN. Không nằm trên đường tải của khách.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LoadtestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadtestServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
