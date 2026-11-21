package com.unbutu.ticket.inventory.reservation;

import com.unbutu.ticket.inventory.stock.StockLedger;
import com.unbutu.ticket.inventory.stock.StockStore;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chốt vé sau khi thanh toán thành công. Thứ tự: đã chốt? → Redis (xoá hold / lấy lại khi trả muộn) → sổ cái Postgres
 * (UPDATE có điều kiện + ghi commits, 1 transaction). Sổ cái từ chối → trả lại Redis → SOLD_OUT (booking hoàn tiền).
 * Postgres là chốt chặn cuối: dù Redis từng mất hold và bán lại, sold không bao giờ vượt tổng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommitService {

    public enum Outcome { COMMITTED, SOLD_OUT }

    public record Result(Outcome outcome, UUID ticketTypeId) {
    }

    private static final class LedgerRejected extends RuntimeException {
        final UUID ticketTypeId;

        LedgerRejected(UUID tt) {
            super(null, null, false, false);
            this.ticketTypeId = tt;
        }
    }

    private final StockStore store;
    private final StockLedger ledger;
    private final TransactionTemplate tx;

    public Result commit(UUID eventId, UUID reservationId, Map<UUID, Integer> items) {
        if (ledger.isCommitted(reservationId)) {
            return new Result(Outcome.COMMITTED, null);           // chốt lại (retry / event lặp) → idempotent
        }
        var redis = store.commit(eventId, reservationId, items);
        if ("SOLD_OUT".equals(redis.outcome())) {
            log.warn("Chốt vé {}: hold đã mất và không còn đủ vé ({})", reservationId, redis.ticketTypeId());
            return new Result(Outcome.SOLD_OUT, redis.ticketTypeId());
        }
        boolean late = "LATE".equals(redis.outcome());
        try {
            tx.executeWithoutResult(s -> {
                ledger.recordCommit(reservationId, eventId, late);
                items.forEach((tt, q) -> {
                    if (!ledger.addSold(tt, q)) {
                        throw new LedgerRejected(tt);
                    }
                });
            });
        } catch (LedgerRejected e) {
            store.compensate(eventId, items);
            log.error("Chốt vé {}: SỔ CÁI từ chối (sold + qty > tổng) hạng {} – Redis đã lệch, hoàn tiền", reservationId, e.ticketTypeId);
            return new Result(Outcome.SOLD_OUT, e.ticketTypeId);
        } catch (org.springframework.dao.DuplicateKeyException race) {
            return new Result(Outcome.COMMITTED, null);           // request song song đã chốt trước
        }
        log.info("Chốt vé {} ({}): {}", reservationId, late ? "trả muộn, lấy lại từ số còn" : "từ hold", items);
        return new Result(Outcome.COMMITTED, null);
    }
}
