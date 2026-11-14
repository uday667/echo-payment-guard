package com.echo.paymentguard.repository;

import com.echo.paymentguard.model.ProcessedPaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, String> {
    long countByEventIdStartingWithAndState(String prefix, String state);

    @Modifying
    @Transactional
    @Query("delete from ProcessedPaymentEvent e where e.state = 'COMPLETED' and e.processedAt < :cutoff")
    int deleteCompletedBefore(@Param("cutoff") Instant cutoff);
}
