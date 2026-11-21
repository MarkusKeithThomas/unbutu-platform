package com.unbutu.ticket.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PaymentController {

    record CreatePaymentRequest(@NotNull UUID bookingId, @NotBlank String bookingCode, @Min(1) long amount,
                                @NotBlank String method, @NotNull Instant expireAt, String clientIp) {
    }

    private final PaymentService service;

    // ---------- nội bộ (gateway không route /internal/**) – booking gọi ----------

    @PostMapping("/internal/payments")
    PaymentService.Created create(@Valid @RequestBody CreatePaymentRequest r) {
        return service.create(r.bookingId(), r.bookingCode(), r.amount(), r.method(), r.expireAt(), r.clientIp());
    }

    @PostMapping("/internal/payments/bookings/{bookingId}/refund")
    Map<String, Boolean> refund(@PathVariable UUID bookingId) {
        return Map.of("refunded", service.refund(bookingId));
    }

    // ---------- công khai: cổng thanh toán gọi về ----------

    /** IPN (A14): server→server, có chữ ký. Luôn HTTP 200 + {RspCode, Message} như đặc tả VNPay. */
    @GetMapping("/api/payments/vnpay/ipn")
    PaymentService.IpnResponse ipn(@RequestParam Map<String, String> params) {
        return service.handleIpn(params);
    }

    /** Return (A15): trình duyệt quay về → verify chữ ký → 302 về UI /orders/{bookingId}. Không đổi trạng thái. */
    @GetMapping("/api/payments/vnpay/return")
    ResponseEntity<Void> back(@RequestParam Map<String, String> params) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(service.returnRedirect(params))).build();
    }
}
