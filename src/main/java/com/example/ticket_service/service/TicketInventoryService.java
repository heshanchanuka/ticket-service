package com.example.ticket_service.service;

import com.example.ticket_service.entity.TicketInventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service managing ticket inventory with optimistic concurrency and retry handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketInventoryService {

    private final TicketInventoryOperations operations;
    private final OptimisticRetryHelper retryHelper;

    @Transactional
    public TicketInventory reserveStock(UUID ticketTypeId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to reserve must be greater than zero");
        }
        return retryHelper.executeWithRetry(
                () -> operations.executeReserve(ticketTypeId, quantity),
                "reserveStock"
        );
    }

    @Transactional
    public TicketInventory releaseStock(UUID ticketTypeId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to release must be greater than zero");
        }
        return retryHelper.executeWithRetry(
                () -> operations.executeRelease(ticketTypeId, quantity),
                "releaseStock"
        );
    }
}
