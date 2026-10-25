package com.example.ticket_service.service;

import com.example.ticket_service.entity.TicketInventory;
import com.example.ticket_service.entity.TicketType;
import com.example.ticket_service.exception.ConcurrentInventoryModificationException;
import com.example.ticket_service.exception.InsufficientStockException;
import com.example.ticket_service.repository.TicketInventoryRepository;
import com.example.ticket_service.repository.TicketTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TicketInventoryConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private TicketInventoryService ticketInventoryService;

    @Autowired
    private TicketInventoryRepository ticketInventoryRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Test
    @DisplayName("Concurrently reserve stock with 20 threads: availableQuantity never goes negative and total reserved <= totalQuantity")
    void testConcurrentReserveStock() throws InterruptedException {
        int initialTotalQuantity = 10;

        TicketType ticketType = ticketTypeRepository.save(TicketType.builder()
                .eventId(UUID.randomUUID())
                .name("General Admission")
                .description("Concert General Admission")
                .price(new BigDecimal("75.00"))
                .totalQuantity(initialTotalQuantity)
                .build());

        ticketInventoryRepository.save(TicketInventory.builder()
                .ticketTypeId(ticketType.getId())
                .availableQuantity(initialTotalQuantity)
                .version(0L)
                .build());

        int numberOfThreads = 20;
        int requestedPerThread = 1;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);
        AtomicInteger concurrentConflictCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    ticketInventoryService.reserveStock(ticketType.getId(), requestedPerThread);
                    successfulReservations.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    insufficientStockCount.incrementAndGet();
                } catch (ConcurrentInventoryModificationException ex) {
                    concurrentConflictCount.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected error
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all threads simultaneously
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent tasks should complete within timeout");

        TicketInventory finalInventory = ticketInventoryRepository.findByTicketTypeId(ticketType.getId())
                .orElseThrow();

        int available = finalInventory.getAvailableQuantity();
        int reservedTotal = successfulReservations.get() * requestedPerThread;

        // Assert that availableQuantity never goes negative
        assertTrue(available >= 0, "availableQuantity must never be negative, actual: " + available);

        // Assert that the sum of successfully reserved quantities never exceeds the original totalQuantity
        assertTrue(reservedTotal <= initialTotalQuantity,
                "Total reserved (" + reservedTotal + ") must not exceed totalQuantity (" + initialTotalQuantity + ")");

        // Inventory conservation: available + reserved == total
        assertEquals(initialTotalQuantity, available + reservedTotal,
                "Sum of available quantity and successfully reserved quantity must equal original total quantity");
    }
}
