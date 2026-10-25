package com.example.ticket_service.service;

import com.example.ticket_service.entity.TicketInventory;
import com.example.ticket_service.exception.InsufficientStockException;
import com.example.ticket_service.exception.TicketInventoryNotFoundException;
import com.example.ticket_service.repository.TicketInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Executes individual ticket inventory operations in an isolated transaction.
 * Using REQUIRES_NEW ensures that on optimistic lock failure, rollback of the failed
 * attempt does not taint the caller's transaction and each retry gets a fresh transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketInventoryOperations {

    private final TicketInventoryRepository ticketInventoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TicketInventory executeReserve(UUID ticketTypeId, int quantity) {
        TicketInventory inventory = ticketInventoryRepository.findByTicketTypeId(ticketTypeId)
                .orElseThrow(() -> new TicketInventoryNotFoundException(
                        "Ticket inventory not found for ticket type: " + ticketTypeId));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for ticket type " + ticketTypeId +
                    ". Available: " + inventory.getAvailableQuantity() + ", requested: " + quantity);
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        return ticketInventoryRepository.saveAndFlush(inventory);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TicketInventory executeRelease(UUID ticketTypeId, int quantity) {
        TicketInventory inventory = ticketInventoryRepository.findByTicketTypeId(ticketTypeId)
                .orElseThrow(() -> new TicketInventoryNotFoundException(
                        "Ticket inventory not found for ticket type: " + ticketTypeId));

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        return ticketInventoryRepository.saveAndFlush(inventory);
    }
}
