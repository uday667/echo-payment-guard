package com.echo.paymentguard.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentEvent(String eventId, String debtorAccount, String creditorAccount,
                           BigDecimal amount, String currency, String channel,
                           String reference, OffsetDateTime initiatedAt) {
    public PaymentRequest toPaymentRequest() {
        return new PaymentRequest(debtorAccount, creditorAccount, amount, currency,
                channel, reference, initiatedAt);
    }
}
