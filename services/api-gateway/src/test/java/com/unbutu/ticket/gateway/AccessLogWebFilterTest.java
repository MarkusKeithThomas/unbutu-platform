package com.unbutu.ticket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogWebFilterTest {

    private final AccessLogWebFilter filter = new AccessLogWebFilter();

    @Test
    void logsOneLinePerRequestWithStatus(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/catalog/events").header("X-Request-Id", "req-1"));

        filter.filter(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return Mono.empty();
        }).block();

        assertThat(output).contains("GET /api/catalog/events 404");
    }

    @Test
    void skipsActuator(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health/readiness"));

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(output).doesNotContain("/actuator/health/readiness");
    }

    @Test
    void usesForwardedForOnlyBehindLocalProxy() {
        var viaFunnel = MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 50000))
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                .build();
        var direct = MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("198.51.100.9", 50000))
                .header("X-Forwarded-For", "6.6.6.6")   // client tự gửi để giả IP → bỏ qua
                .build();

        assertThat(AccessLogWebFilter.clientAddress(viaFunnel)).isEqualTo("203.0.113.7");
        assertThat(AccessLogWebFilter.clientAddress(direct)).isEqualTo("198.51.100.9");
    }
}
