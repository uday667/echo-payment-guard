package com.echo.paymentguard.dto;

import com.echo.paymentguard.model.AlertAudit;

import java.time.Instant;
import java.util.UUID;

public record AlertAuditResponse(UUID alertId, String fromStatus, String toStatus,
                                 String actor, String note, Instant at) {
    public static AlertAuditResponse from(AlertAudit audit) {
        return new AlertAuditResponse(
                audit.getAlertId(),
                audit.getFromStatus(),
                audit.getToStatus(),
                audit.getActor(),
                audit.getNote(),
                audit.getAt()
        );
    }
}
