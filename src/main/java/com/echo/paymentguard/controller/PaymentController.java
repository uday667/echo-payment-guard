package com.echo.paymentguard.controller;

import com.echo.paymentguard.dto.ApiError;
import com.echo.paymentguard.dto.PaymentRequest;
import com.echo.paymentguard.dto.PaymentResponse;
import com.echo.paymentguard.service.IntakeService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final IntakeService intakeService;

    public PaymentController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping("/payments")
    public ResponseEntity<?> accept(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @RequestBody(required = false) PaymentRequest request) {
        if (key == null || key.isBlank()) return ResponseEntity.badRequest().body(new ApiError("Idempotency-Key header is required"));
        if (key.length() > 512) return ResponseEntity.badRequest().body(new ApiError("Idempotency-Key must be at most 512 characters"));
        if (request == null) return ResponseEntity.badRequest().body(new ApiError("Request body is required"));
        String validationError = request.validate();
        if (validationError != null) return ResponseEntity.badRequest().body(new ApiError(validationError));

        IntakeService.IntakeResult result = intakeService.accept(key.trim(), request);
        PaymentResponse body = new PaymentResponse(result.paymentId(), "ACCEPTED");
        return switch (result.outcome()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(body);
            case REPLAYED -> ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
            case MISMATCH -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("Idempotency-Key was already used with a different payment payload"));
        };
    }
}
