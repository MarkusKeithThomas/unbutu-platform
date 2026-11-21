package com.unbutu.ticket.payment.mock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Lỗi "bank-pending": giao dịch PROCESSING tới hạn → ngân hàng ra kết quả SUCCESS → lúc này mới gửi IPN. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
class MockBankSettler {

    private final MockBank bank;
    private final MockIpnSender ipn;

    @Scheduled(fixedDelay = 5000)
    void settle() {
        for (String txnRef : bank.settleDue(50)) {
            log.info("GIẢ LẬP bank-pending: {} ngân hàng xử lý xong → SUCCESS", txnRef);
            ipn.plan(txnRef);
        }
    }
}
