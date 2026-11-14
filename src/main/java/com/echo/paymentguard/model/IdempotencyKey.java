package com.echo.paymentguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    @Id
    @Column(name = "idempotency_key", length = 512)
    private String key;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "payment_id", unique = true)
    private UUID paymentId;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String key, String fingerprint, UUID paymentId) {
        this.key = key;
        this.fingerprint = fingerprint;
        this.paymentId = paymentId;
    }

    public String getKey() { return key; }
    public String getFingerprint() { return fingerprint; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
}
