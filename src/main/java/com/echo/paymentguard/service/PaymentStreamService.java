package com.echo.paymentguard.service;

import com.echo.paymentguard.dto.PaymentEvent;
import com.echo.paymentguard.model.PaymentAlert;
import com.echo.paymentguard.repository.PaymentAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "echo.stream.kafka.enabled", havingValue = "true")
public class PaymentStreamService {
    private final IntakeService intakeService;
    private final PaymentWindowService windowService;
    private final PaymentAlertRepository alertRepository;

    public PaymentStreamService(IntakeService intakeService, PaymentWindowService windowService,
                                PaymentAlertRepository alertRepository) {
        this.intakeService = intakeService;
        this.windowService = windowService;
        this.alertRepository = alertRepository;
    }

    public void process(PaymentEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Payment event and payment fields are required");
        }
        var payment = event.toPaymentRequest();
        String validationError = payment.validate();
        if (validationError != null) throw new IllegalArgumentException(validationError);

        IntakeService.EventIntakeResult intake = intakeService.acceptEvent(event.eventId(), payment);
        if (intake.alreadyCompleted()) return;

        PaymentWindowService.WindowResult result = windowService.record(
                event.eventId(), intake.paymentId().toString(), payment);

        if (result.mostRecentEarlierPaymentId() != null) {
            saveAlert(new PaymentAlert(
                    UUID.randomUUID(), "NEAR_DUPLICATE", "OPEN",
                    intake.paymentId() + "," + result.mostRecentEarlierPaymentId(),
                    null, "NEAR:" + event.eventId(),
                    payment.debtorAccount(), payment.creditorAccount(),
                    payment.amount(), payment.currency().toUpperCase(Locale.ROOT),
                    "Matching payment within the configured event-time window; eventId=" + event.eventId(),
                    Instant.now()
            ));
        }
        if (result.rapidRepeat()) {
            saveAlert(new PaymentAlert(
                    UUID.randomUUID(), "RAPID_REPEAT", "OPEN", intake.paymentId().toString(),
                    null, "RAPID:" + event.eventId(),
                    payment.debtorAccount(), payment.creditorAccount(),
                    payment.amount(), payment.currency().toUpperCase(Locale.ROOT),
                    "Payment pair reached " + result.pairCount()
                            + " events in the configured window; eventId=" + event.eventId(),
                    Instant.now()
            ));
        }
        intakeService.completeEvent(event.eventId());
    }

    private void saveAlert(PaymentAlert alert) {
        if (!alertRepository.existsByDeduplicationKey(alert.getDeduplicationKey())) {
            alertRepository.saveAndFlush(alert);
        }
    }
}
