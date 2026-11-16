package com.unbutu.ticket.platform;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Quy ước chung cho service Spring MVC (README của module). Chỉ bật cho ứng dụng servlet – gateway (WebFlux) dùng
 * filter riêng. Trace header ({@code traceparent}) do OpenTelemetry Java agent tự truyền; ở đây chỉ lo {@code X-Request-Id}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformAutoConfiguration {

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        var registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        var registration = new FilterRegistrationBean<>(new AccessLogFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);   // sau RequestIdFilter → MDC đã có requestId
        return registration;
    }

    /** Gọi service khác qua {@code RestClient.Builder} được inject → tự gửi kèm {@code X-Request-Id} của request hiện tại. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClientCustomizer.class)
    static class RestClientRequestId {

        @Bean
        RestClientCustomizer requestIdRestClientCustomizer() {
            return builder -> builder.requestInterceptor((request, body, execution) -> {
                String requestId = MDC.get(RequestIdFilter.MDC_KEY);
                if (requestId != null && !request.getHeaders().containsHeader(RequestIdFilter.HEADER)) {
                    request.getHeaders().set(RequestIdFilter.HEADER, requestId);
                }
                return execution.execute(request, body);
            });
        }
    }
}
