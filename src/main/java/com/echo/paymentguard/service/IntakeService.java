package com.echo.paymentguard.service;

import com.echo.paymentguard.dto.AlertPageResponse;
import com.echo.paymentguard.dto.AlertResponse;
import com.echo.paymentguard.dto.PaymentRequest;
import com.echo.paymentguard.model.IdempotencyKey;
import com.echo.paymentguard.model.Payment;
import com.echo.paymentguard.model.PaymentAlert;
import com.echo.paymentguard.repository.IdempotencyKeyRepository;
import com.echo.paymentguard.repository.PaymentAlertRepository;
import com.echo.paymentguard.repository.PaymentRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class IntakeService {
    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentAlertRepository alertRepository;
    private final TransactionTemplate transactions;

    public IntakeService(PaymentRepository paymentRepository,
                         IdempotencyKeyRepository idempotencyKeyRepository,
                         PaymentAlertRepository alertRepository,
                         PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.alertRepository = alertRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public IntakeResult accept(String key, PaymentRequest request) {
        String fingerprint = fingerprint(request);
        try {
            return transactions.execute(status -> createOrResolve(key, request, fingerprint));
        } catch (DataIntegrityViolationException conflict) {
            IntakeResult resolved = transactions.execute(status -> resolveExisting(key, request, fingerprint));
            if (resolved == null) throw conflict;
            return resolved;
        }
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

    private String fingerprint(PaymentRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, request.debtorAccount());
            update(digest, request.creditorAccount());
            update(digest, request.amount().stripTrailingZeros().toPlainString());
            update(digest, request.currency().toUpperCase(Locale.ROOT));
            update(digest, request.channel());
            update(digest, request.reference());
            update(digest, request.initiatedAt().toInstant().toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    public enum Outcome { CREATED, REPLAYED, MISMATCH }

    public record IntakeResult(UUID paymentId, Outcome outcome) {
    }
}
