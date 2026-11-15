package com.unbutu.ticket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RequestIdWebFilterTest {

    private final RequestIdWebFilter filter = new RequestIdWebFilter();

    @Test
    void keepsValidIncomingIdAndForwardsItDownstream() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/catalog/events").header("X-Request-Id", "client-req-42"));
        var forwarded = new AtomicReference<ServerWebExchange>();

        filter.filter(exchange, ex -> { forwarded.set(ex); return Mono.empty(); }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Request-Id")).isEqualTo("client-req-42");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo("client-req-42");
    }

    @Test
    void generatesNewIdWhenMissingOrInvalid() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/catalog/events").header("X-Request-Id", "bad id\nforged-log-line"));
        var forwarded = new AtomicReference<ServerWebExchange>();

        filter.filter(exchange, ex -> { forwarded.set(ex); return Mono.empty(); }).block();

        String generated = exchange.getResponse().getHeaders().getFirst("X-Request-Id");
        assertThat(generated).matches("[0-9a-f-]{36}");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Request-Id")).isEqualTo(generated);
    }
}
