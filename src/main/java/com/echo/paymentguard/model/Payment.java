package com.echo.paymentguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private UUID id;

    @Column(name = "debtor_account", nullable = false)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false)
    private String creditorAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String channel;
    private String reference;

    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(UUID id, String debtorAccount, String creditorAccount, BigDecimal amount,
                   String currency, String channel, String reference, Instant initiatedAt, Instant createdAt) {
        this.id = id;
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.amount = amount;
        this.currency = currency;
        this.channel = channel;
        this.reference = reference;
        this.initiatedAt = initiatedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getDebtorAccount() { return debtorAccount; }
    public String getCreditorAccount() { return creditorAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getChannel() { return channel; }
    public String getReference() { return reference; }
    public Instant getInitiatedAt() { return initiatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
