package com.example.ticket_service.service;

import com.example.ticket_service.exception.ConcurrentInventoryModificationException;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Reusable helper that executes operations with optimistic locking retry logic.
 */
@Slf4j
@Component
public class OptimisticRetryHelper {

    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long DEFAULT_INITIAL_BACKOFF_MS = 50L;

    public <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        return executeWithRetry(operation, operationName, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_BACKOFF_MS);
    }

    public <T> T executeWithRetry(Supplier<T> operation, String operationName, int maxRetries, long initialBackoffMs) {
        int retryCount = 0;
        long backoff = initialBackoffMs;

        while (true) {
            try {
                return operation.get();
            } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
                if (retryCount >= maxRetries) {
                    log.error("Optimistic lock conflict in {} after {} retries: {}", operationName, maxRetries, ex.getMessage());
                    throw new ConcurrentInventoryModificationException(
                            "Failed to " + operationName + " after " + maxRetries + " retries due to concurrent modifications",
                            ex
                    );
                }

                retryCount++;
                log.warn("Optimistic lock conflict in {} (retry {}/{}). Backing off for {} ms: {}",
                        operationName, retryCount, maxRetries, backoff, ex.getMessage());

                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ConcurrentInventoryModificationException(
                            "Interrupted while waiting for retry in " + operationName,
                            ie
                    );
                }

                backoff = Math.min(backoff * 2, 500L);
            }
        }
    }
}
