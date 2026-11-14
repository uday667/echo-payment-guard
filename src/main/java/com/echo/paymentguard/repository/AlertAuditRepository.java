package com.echo.paymentguard.repository;

import com.echo.paymentguard.model.AlertAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertAuditRepository extends JpaRepository<AlertAudit, UUID> {
    List<AlertAudit> findByAlertIdOrderByAtAsc(UUID alertId);
}
