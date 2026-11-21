package com.unbutu.ticket.catalog.web;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ trả về client dạng Problem Details, kèm {@code code} ổn định để UI xử lý. */
@Getter
public class CatalogException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CatalogException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static CatalogException notFound(String code, String message) {
        return new CatalogException(HttpStatus.NOT_FOUND, code, message);
    }

    public static CatalogException conflict(String code, String message) {
        return new CatalogException(HttpStatus.CONFLICT, code, message);
    }
}
