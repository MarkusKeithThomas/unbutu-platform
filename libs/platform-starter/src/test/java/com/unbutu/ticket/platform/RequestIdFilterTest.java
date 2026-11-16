package com.unbutu.ticket.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void putsIncomingIdIntoMdcDuringRequestAndClearsAfter() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/catalog/events");
        request.addHeader("X-Request-Id", "req-from-gateway");
        var response = new MockHttpServletResponse();
        var seen = new AtomicReference<String>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get("requestId"));
            }
        });

        assertThat(seen.get()).isEqualTo("req-from-gateway");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-from-gateway");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void generatesIdWhenMissingOrInvalid() throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader("X-Request-Id", "bad id\ninjected");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
    }
}
