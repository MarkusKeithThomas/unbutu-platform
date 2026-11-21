package com.unbutu.ticket.payment.mock.fault;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Kế hoạch gửi IPN cho 1 giao dịch. Bắt đầu = happy case (1 lần gửi ngay, đúng chữ ký). Mỗi {@link IpnFault} được bật
 * sẽ "nắn" kế hoạch (bỏ, trễ, nhân bản, làm hỏng…). Ngân hàng giả lập ghi kế hoạch xuống hàng đợi bền rồi mới gửi.
 */
public final class IpnPlan {

    public enum Tamper { NONE, BAD_SIGNATURE, WRONG_AMOUNT }

    /** 1 lần gửi IPN. fakeNetworkErrors: N lần đầu giả "mất mạng" (ngân hàng retry theo lịch). */
    public static final class Delivery {
        public Duration delay = Duration.ZERO;
        public Tamper tamper = Tamper.NONE;
        public int fakeNetworkErrors;
        public final List<String> notes = new ArrayList<>();

        Delivery copy() {
            var d = new Delivery();
            d.delay = delay;
            d.tamper = tamper;
            d.fakeNetworkErrors = fakeNetworkErrors;
            d.notes.addAll(notes);
            return d;
        }
    }

    private final List<Delivery> deliveries = new ArrayList<>(List.of(new Delivery()));
    private final List<String> applied = new ArrayList<>();

    /** Lần gửi chính (bản "thật"). */
    public Delivery primary() {
        return deliveries.getFirst();
    }

    public List<Delivery> deliveries() {
        return deliveries;
    }

    public void dropAll(String why) {
        deliveries.clear();
        applied.add(why);
    }

    /** Thêm bản sao của lần gửi chính, trễ thêm {@code extra}. */
    public void duplicate(Duration extra, String why) {
        if (deliveries.isEmpty()) {
            return;
        }
        var d = primary().copy();
        d.delay = d.delay.plus(extra);
        d.notes.add(why);
        deliveries.add(d);
    }

    public void note(String fault) {
        applied.add(fault);
    }

    /** Lỗi đã áp (ghi log / hiện cho người luyện). */
    public List<String> applied() {
        return applied;
    }
}
