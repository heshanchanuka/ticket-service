package com.example.ticket_service.exception;

public class ConcurrentInventoryModificationException extends RuntimeException {

    public ConcurrentInventoryModificationException(String message) {
        super(message);
    }

    public ConcurrentInventoryModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
