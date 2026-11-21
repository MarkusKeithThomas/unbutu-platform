package com.unbutu.ticket.payment.mock.fault;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Các lỗi IPN thường gặp ở cổng thanh toán thật. Mỗi lớp = 1 lỗi, bật bằng env MOCK_FAULT_*. */
final class IpnFaults {

    private IpnFaults() {
    }

    /** IPN tới chậm: khách đã về trang kết quả mà đơn vẫn "đang xác nhận". */
    @Component
    @RequiredArgsConstructor
    static class Delay implements IpnFault {
        private final MockFaults cfg;

        public String name() { return "ipn-delay"; }
        public boolean enabled() { return !cfg.ipnDelay().isZero(); }
        public int order() { return 10; }

        public void shape(IpnPlan plan, Dice dice) {
            plan.primary().delay = plan.primary().delay.plus(cfg.ipnDelay());
            plan.primary().notes.add(name() + "=" + cfg.ipnDelay().toSeconds() + "s");
            plan.note(name());
        }
    }

    /** IPN sai chữ ký thay bản thật (giả mạo / ngân hàng lỗi ký) → merchant 97 → chỉ đối soát cứu được. */
    @Component
    @RequiredArgsConstructor
    static class BadSignature implements IpnFault {
        private final MockFaults cfg;

        public String name() { return "ipn-bad-signature"; }
        public boolean enabled() { return cfg.ipnBadSignatureRate() > 0; }
        public int order() { return 20; }

        public void shape(IpnPlan plan, Dice dice) {
            if (dice.roll(cfg.ipnBadSignatureRate())) {
                plan.primary().tamper = IpnPlan.Tamper.BAD_SIGNATURE;
                plan.primary().notes.add(name());
                plan.note(name());
            }
        }
    }

    /** IPN sai số tiền (chữ ký vẫn đúng – lỗi phía ngân hàng) → merchant 04, không được ghi nhận. */
    @Component
    @RequiredArgsConstructor
    static class WrongAmount implements IpnFault {
        private final MockFaults cfg;

        public String name() { return "ipn-wrong-amount"; }
        public boolean enabled() { return cfg.ipnWrongAmountRate() > 0; }
        public int order() { return 20; }

        public void shape(IpnPlan plan, Dice dice) {
            if (plan.primary().tamper == IpnPlan.Tamper.NONE && dice.roll(cfg.ipnWrongAmountRate())) {
                plan.primary().tamper = IpnPlan.Tamper.WRONG_AMOUNT;
                plan.primary().notes.add(name());
                plan.note(name());
            }
        }
    }

    /** 2 lần gửi đầu "mất mạng" → ngân hàng gửi lại theo lịch backoff. */
    @Component
    @RequiredArgsConstructor
    static class NetworkError implements IpnFault {
        private final MockFaults cfg;

        public String name() { return "ipn-network-error"; }
        public boolean enabled() { return cfg.ipnNetworkErrorRate() > 0; }
        public int order() { return 30; }

        public void shape(IpnPlan plan, Dice dice) {
            if (dice.roll(cfg.ipnNetworkErrorRate())) {
                plan.primary().fakeNetworkErrors = 2;
                plan.primary().notes.add(name() + "×2");
                plan.note(name());
            }
        }
    }

    /** Ngân hàng gửi IPN 3 lần (bản thật + 2 bản sao sau 2 s, 5 s) → merchant phải trả 02 cho bản lặp. */
    @Component
    @RequiredArgsConstructor
    static class Duplicate implements IpnFault {
        private final MockFaults cfg;

        public String name() { return "ipn-duplicate"; }
        public boolean enabled() { return cfg.ipnDuplicateRate() > 0; }
        public int order() { return 40; }

        public void shape(IpnPlan plan, Dice dice) {
            if (dice.roll(cfg.ipnDuplicateRate())) {
                plan.duplicate(Duration.ofSeconds(2), name() + "#2");
                plan.duplicate(Duration.ofSeconds(5), name() + "#3");
                plan.note(name());
            }
        }
    }

    /** IPN không bao giờ tới (mọi bản) → đơn treo AWAITING_PAYMENT tới khi job đối soát hỏi querydr. */
    @Component
    @RequiredArgsConstructor
    static class Drop implements IpnFault {
        private final MockFaults cfg;

        public String name() { return "ipn-drop"; }
        public boolean enabled() { return cfg.ipnDropRate() > 0; }
        public int order() { return 1000; }   // sau cùng: bỏ hết những gì đã lên kế hoạch

        public void shape(IpnPlan plan, Dice dice) {
            if (dice.roll(cfg.ipnDropRate())) {
                plan.dropAll(name());
            }
        }
    }
}
