package com.echo.paymentguard.dto;

import com.echo.paymentguard.model.PaymentAlert;
import com.echo.paymentguard.util.AccountMasker;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertResponse(UUID id, String type, String status, List<UUID> paymentIds,
                            String debtorAccount, String creditorAccount, BigDecimal amount,
                            String currency, String details, Instant createdAt) {
    public static AlertResponse from(PaymentAlert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getType(),
                alert.getStatus(),
                java.util.Arrays.stream(alert.getPaymentIds().split(","))
                        .map(UUID::fromString)
                        .toList(),
                AccountMasker.mask(alert.getDebtorAccount()),
                AccountMasker.mask(alert.getCreditorAccount()),
                alert.getAmount(),
                alert.getCurrency(),
                alert.getDetails(),
                alert.getCreatedAt()
        );
    }
}
