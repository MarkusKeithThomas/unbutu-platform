package com.unbutu.ticket.inventory.web;

import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class InventoryException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final UUID ticketTypeId;

    public InventoryException(HttpStatus status, String code, String message, UUID ticketTypeId) {
        super(message);
        this.status = status;
        this.code = code;
        this.ticketTypeId = ticketTypeId;
    }

    public static InventoryException conflict(String code, String message, UUID ticketTypeId) {
        return new InventoryException(HttpStatus.CONFLICT, code, message, ticketTypeId);
    }
}
