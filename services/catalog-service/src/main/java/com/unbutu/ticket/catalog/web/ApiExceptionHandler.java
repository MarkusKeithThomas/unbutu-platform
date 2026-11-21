package com.unbutu.ticket.catalog.web;

import com.unbutu.ticket.platform.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Lỗi nghiệp vụ → RFC 9457 Problem Details + {@code code} + {@code requestId}. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(CatalogException.class)
    ResponseEntity<ProblemDetail> handle(CatalogException e) {
        return problem(e.getStatus(), e.getCode(), e.getMessage());
    }

    /** Lưới an toàn khi 2 request cùng lúc vượt qua kiểm tra trùng/ràng buộc ở code (constraint DB chặn). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handle(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "CONFLICT", "Dữ liệu vừa bị thay đổi hoặc trùng, vui lòng tải lại");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(pd);
    }
}
