package com.echo.paymentguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_payment_events")
public class ProcessedPaymentEvent {
    @Id
    @Column(name = "event_id", length = 256)
    private String eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false, length = 16)
    private String state;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedPaymentEvent() {
    }

    public ProcessedPaymentEvent(String eventId, UUID paymentId, String state, Instant processedAt) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.state = state;
        this.processedAt = processedAt;
    }

    public String getEventId() { return eventId; }
    public UUID getPaymentId() { return paymentId; }
    public String getState() { return state; }
    public Instant getProcessedAt() { return processedAt; }
    public void setState(String state) { this.state = state; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
