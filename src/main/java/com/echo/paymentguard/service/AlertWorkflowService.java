package com.echo.paymentguard.service;

import com.echo.paymentguard.dto.AlertAuditResponse;
import com.echo.paymentguard.exception.BadRequestException;
import com.echo.paymentguard.exception.AlertNotFoundException;
import com.echo.paymentguard.exception.AlertTransitionException;
import com.echo.paymentguard.model.AlertAudit;
import com.echo.paymentguard.model.PaymentAlert;
import com.echo.paymentguard.repository.AlertAuditRepository;
import com.echo.paymentguard.repository.PaymentAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AlertWorkflowService {
    private static final List<String> ALLOWED_STATUSES = List.of("REVIEWED", "DISMISSED");

    private final PaymentAlertRepository alertRepository;
    private final AlertAuditRepository auditRepository;

    public AlertWorkflowService(PaymentAlertRepository alertRepository, AlertAuditRepository auditRepository) {
        this.alertRepository = alertRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public void updateStatus(UUID alertId, String requestedStatus, String actor, String note) {
        String toStatus = requestedStatus == null ? "" : requestedStatus.toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(toStatus)) {
            throw new AlertTransitionException("status must be REVIEWED or DISMISSED");
        }

        PaymentAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException("Alert was not found"));
        int changed = alertRepository.transitionOpenAlert(alertId, toStatus);
        if (changed != 1) {
            throw new AlertTransitionException("Only OPEN alerts can be reviewed or dismissed");
        }

        String auditActor = actor == null || actor.isBlank() ? "investigator" : actor.trim();
        String auditNote = note == null ? "" : note.trim();
        if (auditActor.length() > 128) throw new BadRequestException("X-User must be at most 128 characters");
        if (auditNote.length() > 1000) throw new BadRequestException("note must be at most 1000 characters");

        auditRepository.save(new AlertAudit(
                UUID.randomUUID(),
                alert.getId(),
                "OPEN",
                toStatus,
                auditActor,
                auditNote,
                Instant.now()
        ));
    }

    @Transactional(readOnly = true)
    public List<AlertAuditResponse> getAudit(UUID alertId) {
        if (!alertRepository.existsById(alertId)) {
            throw new AlertNotFoundException("Alert was not found");
        }
        return auditRepository.findByAlertIdOrderByAtAsc(alertId).stream()
                .map(AlertAuditResponse::from)
                .toList();
    }
}
