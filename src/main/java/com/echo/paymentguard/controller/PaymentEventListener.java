package com.echo.paymentguard.controller;

import com.echo.paymentguard.dto.PaymentEvent;
import com.echo.paymentguard.service.PaymentStreamService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "echo.stream.kafka.enabled", havingValue = "true")
public class PaymentEventListener {
    private final ObjectMapper objectMapper;
    private final PaymentStreamService streamService;

    public PaymentEventListener(ObjectMapper objectMapper, PaymentStreamService streamService) {
        this.objectMapper = objectMapper;
        this.streamService = streamService;
    }

    @KafkaListener(topics = "payments.incoming", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        PaymentEvent event;
        try {
            event = objectMapper.readValue(message, PaymentEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to process payment event", exception);
        }
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        streamService.process(event);
    }
}
