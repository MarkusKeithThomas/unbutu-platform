package com.unbutu.ticket.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

// Khởi động toàn bộ gateway: bắt lỗi cấu hình route trong application.yml.
@SpringBootTest
class ApiGatewayApplicationTest {

    @Autowired
    RouteLocator routes;

    @Test
    void catalogRouteIsConfigured() {
        assertThat(routes.getRoutes().map(r -> r.getId()).collectList().block()).containsExactly("catalog");
    }
}
