package com.echo.paymentguard.controller;

import com.echo.paymentguard.dto.AlertAuditResponse;
import com.echo.paymentguard.dto.AlertPageResponse;
import com.echo.paymentguard.dto.AlertStatusUpdateRequest;
import com.echo.paymentguard.dto.StreamStatusResponse;
import com.echo.paymentguard.exception.BadRequestException;
import com.echo.paymentguard.service.AlertWorkflowService;
import com.echo.paymentguard.service.IntakeService;
import com.echo.paymentguard.repository.ProcessedPaymentEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final IntakeService intakeService;
    private final AlertWorkflowService workflowService;
    private final ProcessedPaymentEventRepository processedEventRepository;

    public AlertController(IntakeService intakeService, AlertWorkflowService workflowService,
                           ProcessedPaymentEventRepository processedEventRepository) {
        this.intakeService = intakeService;
        this.workflowService = workflowService;
        this.processedEventRepository = processedEventRepository;
    }

    @GetMapping
    public AlertPageResponse alerts(@RequestParam(required = false) String type,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new BadRequestException("page must be zero or greater");
        if (size < 1 || size > 100) throw new BadRequestException("size must be between 1 and 100");
        if (type != null && !type.isBlank()
                && !List.of("KEY_REUSE_MISMATCH", "NEAR_DUPLICATE", "RAPID_REPEAT").contains(type)) {
            throw new BadRequestException("type is not supported");
        }
        if (status != null && !status.isBlank()
                && !status.equals("OPEN") && !status.equals("REVIEWED") && !status.equals("DISMISSED")) {
            throw new BadRequestException("status must be OPEN, REVIEWED, or DISMISSED");
        }
        return intakeService.listAlerts(blankToNull(type), blankToNull(status), page, size);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id,
                                             @RequestBody AlertStatusUpdateRequest request,
                                             @RequestHeader(value = "X-User", required = false) String actor) {
        if (request == null) throw new BadRequestException("Request body is required");
        workflowService.updateStatus(id, request.status(), actor, request.note());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/audit")
    public List<AlertAuditResponse> audit(@PathVariable UUID id) {
        return workflowService.getAudit(id);
    }

    @GetMapping("/stream-status")
    public StreamStatusResponse streamStatus(@RequestParam String runId) {
        if (runId == null || !runId.matches("[a-f0-9]{12}")) {
            throw new BadRequestException("runId must be a 12-character lowercase hexadecimal value");
        }
        return new StreamStatusResponse(runId,
                processedEventRepository.countByEventIdStartingWithAndState(runId + "-", "COMPLETED"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
