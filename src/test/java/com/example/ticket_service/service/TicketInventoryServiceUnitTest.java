package com.example.ticket_service.service;

import com.example.ticket_service.entity.TicketInventory;
import com.example.ticket_service.exception.ConcurrentInventoryModificationException;
import com.example.ticket_service.exception.InsufficientStockException;
import com.example.ticket_service.repository.TicketInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketInventoryServiceUnitTest {

    @Mock
    private TicketInventoryRepository ticketInventoryRepository;

    private OptimisticRetryHelper retryHelper;
    private TicketInventoryOperations operations;
    private TicketInventoryService ticketInventoryService;

    private UUID ticketTypeId;

    @BeforeEach
    void setUp() {
        retryHelper = new OptimisticRetryHelper();
        operations = new TicketInventoryOperations(ticketInventoryRepository);
        ticketInventoryService = new TicketInventoryService(operations, retryHelper);

        ticketTypeId = UUID.randomUUID();
    }

    private TicketInventory createInventory(int quantity, long version) {
        return TicketInventory.builder()
                .id(UUID.randomUUID())
                .ticketTypeId(ticketTypeId)
                .availableQuantity(quantity)
                .version(version)
                .build();
    }

    @Test
    @DisplayName("Should successfully reserve stock when sufficient quantity is available")
    void shouldReserveStockSuccessfully() {
        when(ticketInventoryRepository.findByTicketTypeId(ticketTypeId))
                .thenAnswer(inv -> Optional.of(createInventory(10, 1L)));
        when(ticketInventoryRepository.saveAndFlush(any(TicketInventory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TicketInventory updated = ticketInventoryService.reserveStock(ticketTypeId, 3);

        assertNotNull(updated);
        assertEquals(7, updated.getAvailableQuantity());
        verify(ticketInventoryRepository).saveAndFlush(any(TicketInventory.class));
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when requested quantity exceeds available")
    void shouldThrowWhenInsufficientStock() {
        when(ticketInventoryRepository.findByTicketTypeId(ticketTypeId))
                .thenAnswer(inv -> Optional.of(createInventory(10, 1L)));

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> ticketInventoryService.reserveStock(ticketTypeId, 15));

        assertTrue(ex.getMessage().contains("Insufficient stock"));
        verify(ticketInventoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should successfully release stock and increment available quantity")
    void shouldReleaseStockSuccessfully() {
        when(ticketInventoryRepository.findByTicketTypeId(ticketTypeId))
                .thenAnswer(inv -> Optional.of(createInventory(10, 1L)));
        when(ticketInventoryRepository.saveAndFlush(any(TicketInventory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TicketInventory updated = ticketInventoryService.releaseStock(ticketTypeId, 5);

        assertNotNull(updated);
        assertEquals(15, updated.getAvailableQuantity());
        verify(ticketInventoryRepository).saveAndFlush(any(TicketInventory.class));
    }

    @Test
    @DisplayName("Should retry on optimistic lock conflict and succeed on subsequent attempt")
    void shouldRetryOnOptimisticLockFailureAndSucceed() {
        AtomicInteger attempts = new AtomicInteger(0);
        when(ticketInventoryRepository.findByTicketTypeId(ticketTypeId))
                .thenAnswer(inv -> Optional.of(createInventory(10, 1L)));
        when(ticketInventoryRepository.saveAndFlush(any(TicketInventory.class))).thenAnswer(inv -> {
            if (attempts.incrementAndGet() == 1) {
                throw new OptimisticLockingFailureException("Version conflict on attempt 1");
            }
            return inv.getArgument(0);
        });

        TicketInventory updated = ticketInventoryService.reserveStock(ticketTypeId, 2);

        assertNotNull(updated);
        assertEquals(8, updated.getAvailableQuantity());
        assertEquals(2, attempts.get());
    }

    @Test
    @DisplayName("Should exhaust retries and throw ConcurrentInventoryModificationException")
    void shouldThrowConcurrentModificationWhenRetriesExhausted() {
        when(ticketInventoryRepository.findByTicketTypeId(ticketTypeId))
                .thenAnswer(inv -> Optional.of(createInventory(10, 1L)));
        when(ticketInventoryRepository.saveAndFlush(any(TicketInventory.class)))
                .thenThrow(new OptimisticLockingFailureException("Persistent version conflict"));

        ConcurrentInventoryModificationException ex = assertThrows(
                ConcurrentInventoryModificationException.class,
                () -> ticketInventoryService.reserveStock(ticketTypeId, 2)
        );

        assertTrue(ex.getMessage().contains("after 3 retries due to concurrent modifications"));
    }
}
