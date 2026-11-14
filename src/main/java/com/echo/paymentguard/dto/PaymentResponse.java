package com.echo.paymentguard.dto;

import java.util.UUID;

public record PaymentResponse(UUID paymentId, String status) {
}
