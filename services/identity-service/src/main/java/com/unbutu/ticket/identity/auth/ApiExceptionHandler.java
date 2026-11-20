package com.unbutu.ticket.identity.auth;

import com.unbutu.ticket.platform.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Lỗi nghiệp vụ → RFC 9457 Problem Details + {@code code} + {@code requestId}. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ProblemDetail> handle(AuthException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        pd.setProperty("code", e.getCode());
        pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(e.getStatus()).body(pd);
    }
}
