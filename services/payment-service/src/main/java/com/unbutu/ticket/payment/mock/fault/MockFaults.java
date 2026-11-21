package com.unbutu.ticket.payment.mock.fault;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình lỗi giả lập (payment.mock.faults.*, env MOCK_FAULT_*). Mặc định 0 = happy case. */
@ConfigurationProperties("payment.mock.faults")
public record MockFaults(double ipnDropRate, Duration ipnDelay, double ipnDuplicateRate, double ipnNetworkErrorRate,
                         double ipnBadSignatureRate, double ipnWrongAmountRate, double bankPendingRate,
                         Duration bankPendingDuration, double querydrErrorRate) {

    public MockFaults {
        ipnDelay = ipnDelay == null ? Duration.ZERO : ipnDelay;
        bankPendingDuration = bankPendingDuration == null ? Duration.ofMinutes(3) : bankPendingDuration;
    }
}
