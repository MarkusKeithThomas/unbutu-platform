package com.unbutu.ticket.payment.mock.fault;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Tung xúc xắc cho lỗi theo tỉ lệ (tách ra để sau này test cố định được). */
@Component
public class Dice {

    public boolean roll(double rate) {
        return rate > 0 && (rate >= 1 || ThreadLocalRandom.current().nextDouble() < rate);
    }
}
