package com.example.ticket_service.exception;

public class TicketInventoryNotFoundException extends RuntimeException {

    public TicketInventoryNotFoundException(String message) {
        super(message);
    }
}
