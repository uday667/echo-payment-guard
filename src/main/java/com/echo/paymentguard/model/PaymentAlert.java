package com.echo.paymentguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class PaymentAlert {
    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "payment_ids", nullable = false)
    private String paymentIds;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "debtor_account", nullable = false)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false)
    private String creditorAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentAlert() {
    }

    public PaymentAlert(UUID id, String type, String status, String paymentIds, String idempotencyKey,
                        String debtorAccount, String creditorAccount, BigDecimal amount,
                        String currency, String details, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.paymentIds = paymentIds;
        this.idempotencyKey = idempotencyKey;
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.amount = amount;
        this.currency = currency;
        this.details = details;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getPaymentIds() { return paymentIds; }
    public String getDebtorAccount() { return debtorAccount; }
    public String getCreditorAccount() { return creditorAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
