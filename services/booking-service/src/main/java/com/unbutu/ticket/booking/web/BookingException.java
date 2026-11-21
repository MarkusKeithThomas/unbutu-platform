package com.unbutu.ticket.booking.web;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ → Problem Details với {@code code} ổn định (UI xử lý theo code) + thuộc tính kèm theo. */
@Getter
public class BookingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> extra;

    public BookingException(HttpStatus status, String code, String message, Map<String, Object> extra) {
        super(message);
        this.status = status;
        this.code = code;
        this.extra = extra;
    }

    public BookingException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public static BookingException notFound() {
        return new BookingException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Không tìm thấy đơn hàng");
    }
}
