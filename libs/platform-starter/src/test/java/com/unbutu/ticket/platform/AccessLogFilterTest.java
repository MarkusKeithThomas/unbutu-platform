package com.unbutu.ticket.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();

    @Test
    void logsRequestLine(CapturedOutput output) throws Exception {
        var response = new MockHttpServletResponse();
        response.setStatus(201);

        filter.doFilter(new MockHttpServletRequest("POST", "/api/catalog/events"), response, new MockFilterChain());

        assertThat(output).contains("POST /api/catalog/events 201");
    }

    @Test
    void skipsActuator(CapturedOutput output) throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health/readiness"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(output).doesNotContain("/actuator/health/readiness");
    }
}
