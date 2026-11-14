package com.echo.paymentguard.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class PaymentEventCleanup {
    private final ProcessedPaymentEventRepository repository;
    private final Duration retention;

    public PaymentEventCleanup(ProcessedPaymentEventRepository repository,
                              @Value("${echo.stream.event-id-retention:90d}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${echo.stream.cleanup-interval:3600000}")
    public void deleteExpiredEventReceipts() {
        repository.deleteCompletedBefore(Instant.now().minus(retention));
    }
}
