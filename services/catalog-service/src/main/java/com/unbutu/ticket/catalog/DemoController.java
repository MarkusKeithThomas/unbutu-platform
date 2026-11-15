package com.unbutu.ticket.catalog;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// API demo công khai để thử nhanh luồng Internet → Funnel → gateway → catalog-service.
@RestController
class DemoController {

    @GetMapping(value = "/api/catalog-service/bich-phuong", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    String bichPhuong() {
        return "Xin chào người đẹp Bích Phượng";
    }
}
