package com.echo.paymentguard.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentRequest(
        String debtorAccount,
        String creditorAccount,
        BigDecimal amount,
        String currency,
        String channel,
        String reference,
        OffsetDateTime initiatedAt
) {
    public String validate() {
        if (debtorAccount == null || debtorAccount.isBlank()) return "debtorAccount must not be blank";
        if (creditorAccount == null || creditorAccount.isBlank()) return "creditorAccount must not be blank";
        if (debtorAccount.length() > 128) return "debtorAccount must be at most 128 characters";
        if (creditorAccount.length() > 128) return "creditorAccount must be at most 128 characters";
        if (amount == null || amount.signum() <= 0) return "amount must be greater than zero";
        if (currency == null || !currency.matches("[A-Za-z]{3}")) return "currency must be a 3-letter code";
        if (channel != null && channel.length() > 128) return "channel must be at most 128 characters";
        if (reference != null && reference.length() > 255) return "reference must be at most 255 characters";
        if (initiatedAt == null) return "initiatedAt is required";
        return null;
    }
}
