package com.unbutu.ticket.inventory.web;

import com.unbutu.ticket.platform.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Lỗi nghiệp vụ → RFC 9457 Problem Details + code + requestId (+ ticketTypeId hết vé để UI tô đúng dòng). */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InventoryException.class)
    ResponseEntity<ProblemDetail> handle(InventoryException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        pd.setProperty("code", e.getCode());
        if (e.getTicketTypeId() != null) {
            pd.setProperty("ticketTypeId", e.getTicketTypeId());
        }
        pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(e.getStatus()).body(pd);
    }
}
