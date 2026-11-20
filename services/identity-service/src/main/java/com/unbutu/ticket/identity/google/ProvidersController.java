package com.unbutu.ticket.identity.google;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** UI hỏi cách đăng nhập nào đang bật (ẩn nút Google khi chưa cấu hình GOOGLE_CLIENT_ID). */
@RestController
@RequiredArgsConstructor
class ProvidersController {

    private final GoogleProperties google;

    @GetMapping("/api/identity/providers")
    Map<String, Object> providers() {
        return Map.of("password", true, "google", google.enabled());
    }
}
