package com.echo.paymentguard.service;

import com.echo.paymentguard.dto.AlertPageResponse;
import com.echo.paymentguard.dto.AlertResponse;
import com.echo.paymentguard.dto.PaymentRequest;
import com.echo.paymentguard.model.IdempotencyKey;
import com.echo.paymentguard.model.Payment;
import com.echo.paymentguard.model.PaymentAlert;
import com.echo.paymentguard.model.ProcessedPaymentEvent;
import com.echo.paymentguard.repository.IdempotencyKeyRepository;
import com.echo.paymentguard.repository.PaymentAlertRepository;
import com.echo.paymentguard.repository.PaymentRepository;
import com.echo.paymentguard.repository.ProcessedPaymentEventRepository;
import com.echo.paymentguard.util.PaymentFingerprint;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class IntakeService {
    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentAlertRepository alertRepository;
    private final ProcessedPaymentEventRepository processedEventRepository;
    private final TransactionTemplate transactions;

    public IntakeService(PaymentRepository paymentRepository,
                         IdempotencyKeyRepository idempotencyKeyRepository,
                         PaymentAlertRepository alertRepository,
                         ProcessedPaymentEventRepository processedEventRepository,
                         PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.alertRepository = alertRepository;
        this.processedEventRepository = processedEventRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public IntakeResult accept(String key, PaymentRequest request) {
        String fingerprint = PaymentFingerprint.of(request);
        try {
            return transactions.execute(status -> createOrResolve(key, request, fingerprint));
        } catch (DataIntegrityViolationException conflict) {
            IntakeResult resolved = transactions.execute(status -> resolveExisting(key, request, fingerprint));
            if (resolved == null) throw conflict;
            return resolved;
        }
    }

    public EventIntakeResult acceptEvent(String eventId, PaymentRequest request) {
        if (eventId == null || eventId.isBlank() || eventId.length() > 256) {
            throw new IllegalArgumentException("eventId must contain 1 to 256 characters");
        }
        String fingerprint = PaymentFingerprint.of(request);
        try {
            return transactions.execute(status -> {
                ProcessedPaymentEvent previous = processedEventRepository.findById(eventId).orElse(null);
                if (previous != null && previous.getState().equals("COMPLETED")) {
                    return new EventIntakeResult(previous.getPaymentId(), false, true);
                }

                IntakeResult intake = createOrResolve("stream:" + eventId, request, fingerprint);
                if (intake.outcome() == Outcome.MISMATCH) {
                    processedEventRepository.saveAndFlush(new ProcessedPaymentEvent(
                            eventId, intake.paymentId(), "COMPLETED", Instant.now()));
                    return new EventIntakeResult(intake.paymentId(), false, true);
                }

                ProcessedPaymentEvent event = previous == null
                        ? new ProcessedPaymentEvent(eventId, intake.paymentId(), "PROCESSING", Instant.now())
                        : previous;
                event.setState("PROCESSING");
                event.setProcessedAt(Instant.now());
                processedEventRepository.saveAndFlush(event);
                return new EventIntakeResult(intake.paymentId(),
                        intake.outcome() == Outcome.CREATED, false);
            });
        } catch (DataIntegrityViolationException conflict) {
            EventIntakeResult resolved = transactions.execute(status ->
                    processedEventRepository.findById(eventId)
                            .map(event -> new EventIntakeResult(
                                    event.getPaymentId(),
                                    false,
                                    event.getState().equals("COMPLETED")))
                            .orElse(null));
            if (resolved == null) throw conflict;
            return resolved;
        }
    }

    public void completeEvent(String eventId) {
        transactions.executeWithoutResult(status -> {
            ProcessedPaymentEvent event = processedEventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Payment event reservation was not found"));
            event.setState("COMPLETED");
            event.setProcessedAt(Instant.now());
            processedEventRepository.save(event);
        });
    }

    private IntakeResult createOrResolve(String key, PaymentRequest request, String fingerprint) {
        IdempotencyKey existing = idempotencyKeyRepository.findById(key).orElse(null);
        if (existing != null) return resolve(existing, key, request, fingerprint);

        IdempotencyKey reservation = idempotencyKeyRepository.saveAndFlush(
                new IdempotencyKey(key, fingerprint, null));
        Instant now = Instant.now();
        Payment payment = new Payment(
                UUID.randomUUID(),
                request.debtorAccount(),
                request.creditorAccount(),
                request.amount(),
                request.currency().toUpperCase(Locale.ROOT),
                request.channel(),
                request.reference(),
                request.initiatedAt().toInstant(),
                now
        );
        paymentRepository.saveAndFlush(payment);
        reservation.setPaymentId(payment.getId());
        idempotencyKeyRepository.saveAndFlush(reservation);
        return new IntakeResult(payment.getId(), Outcome.CREATED);
    }

    private IntakeResult resolveExisting(String key, PaymentRequest request, String fingerprint) {
        IdempotencyKey existing = idempotencyKeyRepository.findById(key).orElse(null);
        return existing == null ? null : resolve(existing, key, request, fingerprint);
    }

    private IntakeResult resolve(IdempotencyKey existing, String key, PaymentRequest request, String fingerprint) {
        if (MessageDigest.isEqual(existing.getFingerprint().getBytes(StandardCharsets.US_ASCII),
                fingerprint.getBytes(StandardCharsets.US_ASCII))) {
            return new IntakeResult(existing.getPaymentId(), Outcome.REPLAYED);
        }
        if (!alertRepository.existsByIdempotencyKey(key)) {
            alertRepository.saveAndFlush(new PaymentAlert(
                    UUID.randomUUID(),
                    "KEY_REUSE_MISMATCH",
                    "OPEN",
                    existing.getPaymentId().toString(),
                    key,
                    request.debtorAccount(),
                    request.creditorAccount(),
                    request.amount(),
                    request.currency().toUpperCase(Locale.ROOT),
                    "Idempotency-Key was reused with a different payment payload",
                    Instant.now()
            ));
        }
        return new IntakeResult(existing.getPaymentId(), Outcome.MISMATCH);
    }

    public AlertPageResponse listAlerts(String type, String status, int page, int size) {
        Page<PaymentAlert> result = alertRepository.search(
                type,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        List<AlertResponse> content = result.getContent().stream().map(AlertResponse::from).toList();
        return new AlertPageResponse(content, page, size, result.getTotalElements());
    }

    public enum Outcome { CREATED, REPLAYED, MISMATCH }

    public record IntakeResult(UUID paymentId, Outcome outcome) {
    }

    public record EventIntakeResult(UUID paymentId, boolean newlyCreated, boolean alreadyCompleted) {
    }
}
