package com.unbutu.ticket.gateway;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Điểm sinh {@code X-Request-Id} của toàn hệ thống: giữ id hợp lệ client gửi lên, nếu không thì sinh mới;
 * forward xuống service phía sau và trả lại cho client. Dùng WebFilter (không phải GlobalFilter của gateway)
 * để áp dụng cả cho request không khớp route (404) và actuator.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdWebFilter implements WebFilter {

    static final String HEADER = "X-Request-Id";

    // Chặn giá trị client tự gửi chứa ký tự lạ/quá dài (log injection).
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = incoming != null && VALID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, requestId)))
                .build();
        // Đặt lúc commit (không phải ngay bây giờ): service phía sau cũng trả X-Request-Id và gateway chép header
        // của nó sang → set() lúc commit ghi đè, response chỉ còn đúng 1 giá trị.
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(HEADER, requestId);
            return Mono.empty();
        });
        return chain.filter(mutated);
    }
}
