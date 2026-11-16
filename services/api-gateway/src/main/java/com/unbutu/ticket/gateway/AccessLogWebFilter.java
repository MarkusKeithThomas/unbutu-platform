package com.unbutu.ticket.gateway;

import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Access log có cấu trúc: 1 dòng/request khi request kết thúc (ARCHITECTURE §4.4, OBS-3).
 * Chạy ngay sau {@link RequestIdWebFilter} nên đã có {@code X-Request-Id}. Trường dùng tên theo OpenTelemetry/ECS
 * để lọc trong SigNoz; {@code trace_id}/{@code span_id} do OpenTelemetry Java agent tự gắn vào MDC.
 * Bỏ qua {@code /actuator/**} (healthcheck mỗi vài giây – chỉ gây nhiễu).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AccessLogWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogWebFilter.class);

    /** Client đóng kết nối trước khi có response (quy ước của nginx). */
    static final int CLIENT_CLOSED_REQUEST = 499;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> write(exchange, signal, start));
    }

    private void write(ServerWebExchange exchange, SignalType signal, long start) {
        ServerHttpRequest request = exchange.getRequest();
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int code = signal == SignalType.CANCEL ? CLIENT_CLOSED_REQUEST : status != null ? status.value() : 200;
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        String method = request.getMethod().name();
        String path = request.getPath().value();

        log.atInfo()
                .addKeyValue("http.request.method", method)
                .addKeyValue("url.path", path)
                .addKeyValue("http.response.status_code", code)
                .addKeyValue("event.duration_ms", durationMs)
                .addKeyValue("client.address", clientAddress(request))
                .addKeyValue("requestId", request.getHeaders().getFirst(RequestIdWebFilter.HEADER))
                .log("{} {} {} {}ms", method, path, code, durationMs);
    }

    /**
     * IP thật của client. Tailscale Funnel → 127.0.0.1:8080 nên remote address luôn là loopback;
     * khi đó lấy phần tử đầu của {@code X-Forwarded-For} do Funnel thêm vào.
     */
    static String clientAddress(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        boolean viaLocalProxy = remote == null || remote.getAddress() == null || remote.getAddress().isLoopbackAddress();
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (viaLocalProxy && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return remote == null ? "unknown" : remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }
}
