package com.unbutu.ticket.booking.booking;

import com.unbutu.ticket.booking.booking.BookingDtos.BookingPage;
import com.unbutu.ticket.booking.booking.BookingDtos.BookingView;
import com.unbutu.ticket.booking.booking.BookingDtos.CreateBookingRequest;
import com.unbutu.ticket.booking.booking.BookingDtos.EventView;
import com.unbutu.ticket.booking.booking.BookingDtos.Line;
import com.unbutu.ticket.booking.inventory.InventoryClient;
import com.unbutu.ticket.booking.pricing.PriceBook;
import com.unbutu.ticket.booking.web.BookingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A9 giữ vé – thứ tự có chủ đích:
 * <ol>
 *   <li>Transaction ngắn: idempotency → khoá (user, sự kiện) → giới hạn 4 vé → tính tiền từ bảng giá → INSERT PENDING → COMMIT.</li>
 *   <li>NGOÀI transaction: gọi inventory giữ vé (Redis Lua). Không giữ kết nối DB trong lúc gọi mạng – pool DB là thứ cạn
 *       đầu tiên khi tải cao.</li>
 *   <li>Hết vé / inventory lỗi → đơn CANCELLED (+ nhả best-effort). Chết giữa bước 1 và 2 → đơn PENDING tự EXPIRED.</li>
 * </ol>
 */
@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookings;
    private final PriceBook priceBook;
    private final InventoryClient inventory;
    private final com.unbutu.ticket.booking.payment.PaymentClient payment;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration holdTtl;
    private final int maxPerUserEvent;
    private final long feePerTicket;

    BookingService(BookingRepository bookings, PriceBook priceBook, InventoryClient inventory,
                   com.unbutu.ticket.booking.payment.PaymentClient payment, TransactionTemplate tx, Clock clock,
                   @Value("${booking.hold-ttl}") Duration holdTtl,
                   @Value("${booking.max-tickets-per-user-event}") int maxPerUserEvent,
                   @Value("${booking.service-fee-per-ticket}") long feePerTicket) {
        this.bookings = bookings;
        this.priceBook = priceBook;
        this.inventory = inventory;
        this.payment = payment;
        this.tx = tx;
        this.clock = clock;
        this.holdTtl = holdTtl;
        this.maxPerUserEvent = maxPerUserEvent;
        this.feePerTicket = feePerTicket;
    }

    /** Kết quả tạo đơn: created=false nghĩa là trả lại đơn cũ (cùng Idempotency-Key). */
    public record CreateResult(BookingView booking, boolean created) {
    }

    public CreateResult create(UUID userId, String idempotencyKey, CreateBookingRequest req) {
        Map<UUID, Integer> items = new TreeMap<>();   // gộp trùng + thứ tự cố định (hash ổn định)
        req.items().forEach(i -> items.merge(i.ticketTypeId(), i.quantity(), Integer::sum));
        String hash = requestHash(req.eventId(), items);

        // ---- 1. transaction ngắn ----
        UUID bookingId = UUID.randomUUID();
        BookingRepository.Row existing;
        try {
            existing = tx.execute(s -> {
                var prev = bookings.findByIdempotencyKey(userId, idempotencyKey);
                if (prev.isPresent()) {
                    return prev.get();
                }
                bookings.lockUserEvent(userId, req.eventId());
                int n = items.values().stream().mapToInt(Integer::intValue).sum();
                int already = bookings.countedTickets(userId, req.eventId());
                if (already + n > maxPerUserEvent) {
                    throw new BookingException(HttpStatus.UNPROCESSABLE_ENTITY, "LIMIT_EXCEEDED",
                            "Mỗi tài khoản mua tối đa " + maxPerUserEvent + " vé cho một sự kiện",
                            Map.of("limit", maxPerUserEvent, "remaining", Math.max(0, maxPerUserEvent - already)));
                }
                List<BookingRepository.ItemRow> lines = price(bookingId, req.eventId(), items);
                long subtotal = lines.stream().mapToLong(l -> l.unitPrice() * l.quantity()).sum();
                long fee = feePerTicket * n;
                bookings.insert(bookingId, userId, req.eventId(), n, subtotal, fee, subtotal + fee, idempotencyKey, hash,
                        clock.instant().plus(holdTtl), lines);
                return null;
            });
        } catch (DuplicateKeyException race) {
            // 2 request cùng Idempotency-Key tới cùng lúc: request kia đã INSERT trước → trả đơn đó.
            existing = bookings.findByIdempotencyKey(userId, idempotencyKey).orElseThrow(() -> race);
        }
        if (existing != null) {
            return new CreateResult(replay(existing, hash), false);
        }

        // ---- 2. giữ vé ở inventory (ngoài transaction) ----
        InventoryClient.ReserveResult r;
        try {
            r = inventory.reserve(req.eventId(), bookingId, items, holdTtl.toSeconds());
        } catch (InventoryClient.UnavailableException e) {
            log.warn("Giữ vé lỗi cho đơn {}: {}", bookingId, e.getMessage());
            bookings.cancelPending(bookingId, "INVENTORY_ERROR");
            inventory.release(req.eventId(), bookingId);   // phòng khi inventory đã giữ nhưng trả lời không tới
            throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "INVENTORY_UNAVAILABLE",
                    "Hệ thống giữ vé đang bận, vui lòng thử lại");
        }
        switch (r.outcome()) {
            case RESERVED -> bookings.setExpiresAt(bookingId, r.expiresAt());   // hạn giữ thật do inventory quyết
            case SOLD_OUT, NOT_ON_SALE -> {
                bookings.cancelPending(bookingId, r.outcome().name());
                throw soldOut(r.outcome().name(), r.ticketTypeId());
            }
        }
        return new CreateResult(get(userId, bookingId), true);
    }

    public BookingView get(UUID userId, UUID bookingId) {
        var row = bookings.findOwned(bookingId, userId).orElseThrow(BookingException::notFound);   // người khác → 404, không lộ tồn tại
        return views(List.of(row)).getFirst();
    }

    public BookingPage mine(UUID userId, int page, int size) {
        var rows = bookings.findMine(userId, page, size);
        boolean hasNext = rows.size() > size;
        return new BookingPage(views(hasNext ? rows.subList(0, size) : rows), page, size, hasNext);
    }

    /**
     * A10 – "Xác nhận thanh toán": lưu người mua → AWAITING_PAYMENT → payment tạo giao dịch (1 PENDING / đơn,
     * bấm lại → cùng URL) → trả URL cổng. Hết hạn giữ → 409 HOLD_EXPIRED.
     */
    public BookingDtos.CheckoutResponse checkout(UUID userId, UUID bookingId, BookingDtos.CheckoutRequest req, String clientIp) {
        var row = bookings.findOwned(bookingId, userId).orElseThrow(BookingException::notFound);
        var buyer = req.buyer();
        String phone = buyer.phone() == null || buyer.phone().isBlank() ? null : buyer.phone().strip();
        if (!bookings.startPayment(bookingId, userId, buyer.name().strip(), buyer.email().strip(), phone, req.method())) {
            if ("CONFIRMED".equals(row.status())) {
                throw new BookingException(HttpStatus.CONFLICT, "ALREADY_PAID", "Đơn hàng đã được thanh toán");
            }
            throw new BookingException(HttpStatus.CONFLICT, "HOLD_EXPIRED", "Đơn đã hết thời gian giữ vé");
        }
        String url = payment.createPayment(bookingId, row.code(), row.total(), req.method(), row.expiresAt(), clientIp);
        return new BookingDtos.CheckoutResponse(url);
    }

    /** A13 "← Chọn lại vé": huỷ + nhả vé. Idempotent: đã huỷ/hết hạn → coi như xong. */
    public void cancel(UUID userId, UUID bookingId) {
        var row = bookings.findOwned(bookingId, userId).orElseThrow(BookingException::notFound);
        if (bookings.cancelPending(bookingId, "USER")) {
            inventory.release(row.eventId(), bookingId);
            return;
        }
        if (!"CANCELLED".equals(row.status()) && !"EXPIRED".equals(row.status())) {
            throw new BookingException(HttpStatus.CONFLICT, "BOOKING_NOT_CANCELLABLE", "Đơn hàng không còn huỷ được");
        }
    }

    // ---------- helpers ----------

    private List<BookingRepository.ItemRow> price(UUID bookingId, UUID eventId, Map<UUID, Integer> items) {
        Map<UUID, PriceBook.Price> prices = priceBook.prices(items.keySet()).stream()
                .collect(Collectors.toMap(PriceBook.Price::ticketTypeId, Function.identity()));
        List<BookingRepository.ItemRow> lines = new ArrayList<>();
        items.forEach((tt, qty) -> {
            var p = prices.get(tt);
            if (p == null || !p.eventId().equals(eventId) || !"ON_SALE".equals(p.status())) {
                throw soldOut("NOT_ON_SALE", tt);
            }
            lines.add(new BookingRepository.ItemRow(bookingId, tt, p.name(), p.price(), qty));
        });
        return lines;
    }

    /** Gọi lại cùng Idempotency-Key: cùng nội dung → trả kết quả cũ (kể cả lỗi hết vé); khác nội dung → 409. */
    private BookingView replay(BookingRepository.Row prev, String hash) {
        if (!prev.requestHash().equals(hash)) {
            throw new BookingException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key đã dùng cho một yêu cầu khác");
        }
        if ("CANCELLED".equals(prev.status()) && ("SOLD_OUT".equals(prev.cancelReason()) || "NOT_ON_SALE".equals(prev.cancelReason()))) {
            throw soldOut(prev.cancelReason(), null);
        }
        return views(List.of(prev)).getFirst();
    }

    private static BookingException soldOut(String code, UUID ticketTypeId) {
        String msg = "SOLD_OUT".equals(code) ? "Hạng vé đã hết hoặc không đủ số lượng" : "Hạng vé không mở bán";
        return new BookingException(HttpStatus.CONFLICT, code, msg,
                ticketTypeId == null ? Map.of() : Map.of("ticketTypeId", ticketTypeId));
    }

    /** N đơn → 2 query (items + event), không N+1. */
    private List<BookingView> views(List<BookingRepository.Row> rows) {
        var items = bookings.items(rows.stream().map(BookingRepository.Row::id).toList());
        var events = priceBook.events(rows.stream().map(BookingRepository.Row::eventId).distinct().toList()).stream()
                .collect(Collectors.toMap(PriceBook.EventInfo::eventId, Function.identity()));
        return rows.stream().map(r -> {
            var ev = events.get(r.eventId());
            var lines = items.getOrDefault(r.id(), List.of()).stream()
                    .map(i -> new Line(i.ticketTypeId(), i.name(), i.unitPrice(), i.quantity(), i.unitPrice() * i.quantity()))
                    .toList();
            return new BookingView(r.id(), r.code(), r.status(), r.cancelReason(),
                    new EventView(r.eventId(), ev == null ? null : ev.title(), ev == null ? null : ev.venue(),
                            ev == null ? null : ev.startsAt()),
                    lines, r.ticketCount(), r.subtotal(), r.fee(), r.total(), r.expiresAt(), r.createdAt());
        }).toList();
    }

    private static String requestHash(UUID eventId, Map<UUID, Integer> items) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(eventId.toString().getBytes(StandardCharsets.UTF_8));
            items.forEach((k, v) -> md.update((k + "=" + v + ";").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
