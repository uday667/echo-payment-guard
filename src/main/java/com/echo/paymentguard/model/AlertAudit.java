package com.echo.paymentguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_audit")
public class AlertAudit {
    @Id
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "from_status", nullable = false, length = 16)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 16)
    private String toStatus;

    @Column(nullable = false, length = 128)
    private String actor;

    @Column(length = 1000)
    private String note;

    @Column(name = "audit_at", nullable = false)
    private Instant at;

    protected AlertAudit() {
    }

    public AlertAudit(UUID id, UUID alertId, String fromStatus, String toStatus,
                      String actor, String note, Instant at) {
        this.id = id;
        this.alertId = alertId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.note = note;
        this.at = at;
    }

    public UUID getId() { return id; }
    public UUID getAlertId() { return alertId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getNote() { return note; }
    public Instant getAt() { return at; }
}
