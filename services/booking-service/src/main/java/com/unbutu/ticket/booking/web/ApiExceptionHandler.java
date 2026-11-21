package com.unbutu.ticket.booking.web;

import com.unbutu.ticket.platform.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(BookingException.class)
    ResponseEntity<ProblemDetail> handle(BookingException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        pd.setProperty("code", e.getCode());
        e.getExtra().forEach(pd::setProperty);
        pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(e.getStatus()).body(pd);
    }
}
