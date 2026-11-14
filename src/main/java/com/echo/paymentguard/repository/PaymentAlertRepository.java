package com.echo.paymentguard.repository;

import com.echo.paymentguard.model.PaymentAlert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface PaymentAlertRepository extends JpaRepository<PaymentAlert, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Transactional
    @Query("update PaymentAlert a set a.status = :status where a.id = :id and a.status = 'OPEN'")
    int transitionOpenAlert(@Param("id") UUID id, @Param("status") String status);

    @Query("""
            select a from PaymentAlert a
            where (:type is null or a.type = :type)
              and (:status is null or a.status = :status)
            """)
    Page<PaymentAlert> search(@Param("type") String type, @Param("status") String status, Pageable pageable);
}
