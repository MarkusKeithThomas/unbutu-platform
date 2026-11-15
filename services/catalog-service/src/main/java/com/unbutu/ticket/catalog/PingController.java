package com.unbutu.ticket.catalog;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PingController {

    @GetMapping("/api/catalog/ping")
    Map<String, String> ping() {
        return Map.of("service", "catalog-service", "status", "ok");
    }
}
