package com.echo.paymentguard.dto;

public record StreamStatusResponse(String runId, long completedEvents) {
}
