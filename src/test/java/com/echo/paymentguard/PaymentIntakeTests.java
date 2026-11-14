package com.echo.paymentguard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=30"
})
@AutoConfigureMockMvc
class PaymentIntakeTests {
    private static final String DEBTOR = "1111222233334444";
    private static final String CREDITOR = "5555666677778888";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.execute("DELETE FROM alert_audit");
        jdbcTemplate.execute("DELETE FROM alerts");
        jdbcTemplate.execute("DELETE FROM idempotency_keys");
        jdbcTemplate.execute("DELETE FROM payments");
    }

    @Test
    void acceptsFirstPaymentAndReturnsOnlyPaymentId() throws Exception {
        MvcResult result = submit("first-key", payload("25.50")).andExpect(status().isCreated()).andReturn();

        String response = result.getResponse().getContentAsString();
        assertNotNull(paymentId(response));
        assertTrue(response.contains("\"status\":\"ACCEPTED\""));
        assertEquals(1, count("payments"));
        assertFalse(result.getResponse().getContentAsString().contains(DEBTOR));
        assertFalse(result.getResponse().getContentAsString().contains(CREDITOR));
    }

    @Test
    void samePayloadReplayReturnsOriginalIdAndCreatesNoPayment() throws Exception {
        String first = submit("replay-key", payload("25.50")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        MvcResult secondResult = submit("replay-key", payload("25.5000"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(paymentId(first), paymentId(secondResult.getResponse().getContentAsString()));
        assertEquals("true", secondResult.getResponse().getHeader("Idempotent-Replayed"));
        assertEquals(1, count("payments"));
    }

    @Test
    void changedPayloadCreatesOneMismatchAlertAndAlertResponseMasksAccounts() throws Exception {
        submit("mismatch-key", payload("25.50")).andExpect(status().isCreated());
        submit("mismatch-key", payload("26.50")).andExpect(status().isConflict());
        submit("mismatch-key", payload("27.50")).andExpect(status().isConflict());

        assertEquals(1, count("payments"));
        assertEquals(1, count("alerts"));
        MvcResult alerts = mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andReturn();
        String response = alerts.getResponse().getContentAsString();
        assertTrue(response.contains("KEY_REUSE_MISMATCH"));
        assertTrue(response.contains("XXXXXXXX4444"));
        assertTrue(response.contains("XXXXXXXX8888"));
        assertFalse(response.contains(DEBTOR));
        assertFalse(response.contains(CREDITOR));
    }

    @Test
    void rejectsMissingKeyAndInvalidAmountWithClearBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("25.50")))
                .andExpect(status().isBadRequest());

        MvcResult invalid = submit("invalid-key", payload("0"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(invalid.getResponse().getContentAsString().contains("amount must be greater than zero"));
        assertEquals(0, count("payments"));
    }

    @Test
    void concurrentSameKeyRequestsCreateExactlyOnePayment() throws Exception {
        int parallelRequests = 20;
        ExecutorService executor = Executors.newFixedThreadPool(parallelRequests);
        CountDownLatch ready = new CountDownLatch(parallelRequests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < parallelRequests; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return submit("parallel-key", payload("25.50")).andReturn();
                }));
            }
            ready.await();
            start.countDown();
            List<String> paymentIds = new ArrayList<>();
            List<String> outcomes = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get();
                int responseStatus = result.getResponse().getStatus();
                String body = result.getResponse().getContentAsString();
                outcomes.add(responseStatus + " " + body);
                assertTrue(responseStatus == 201 || responseStatus == 200, outcomes.toString());
                paymentIds.add(paymentId(body));
            }
            assertEquals(1, new HashSet<>(paymentIds).size(), outcomes.toString());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, count("payments"));
        assertEquals(1, count("idempotency_keys"));
    }

    @Test
    void flywayCreatesRequiredTablesAndAlertQueryIndex() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PAYMENTS'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'ALERTS'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'IDEMPOTENCY_KEYS'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = 'IDX_ALERTS_STATUS_CREATED_AT'",
                Integer.class));
    }

    @Test
    void alertStatusChangesAreAuditedAndClosedAlertsCannotTransitionAgain() throws Exception {
        submit("audit-key", payload("25.50")).andExpect(status().isCreated());
        submit("audit-key", payload("26.50")).andExpect(status().isConflict());
        UUID alertId = jdbcTemplate.queryForObject("SELECT id FROM alerts", UUID.class);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/alerts/" + alertId)
                        .header("X-User", "analyst-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REVIEWED","note":"Validated with the vendor"}
                                """))
                .andExpect(status().isNoContent());
        assertEquals(1, count("alert_audit"));

        MvcResult audit = mockMvc.perform(get("/api/alerts/" + alertId + "/audit"))
                .andExpect(status().isOk())
                .andReturn();
        String auditBody = audit.getResponse().getContentAsString();
        assertTrue(auditBody.contains("\"fromStatus\":\"OPEN\""));
        assertTrue(auditBody.contains("\"toStatus\":\"REVIEWED\""));
        assertTrue(auditBody.contains("\"actor\":\"analyst-01\""));
        assertTrue(auditBody.contains("Validated with the vendor"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/alerts/" + alertId)
                        .header("X-User", "analyst-02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISMISSED","note":"Cannot change a reviewed alert"}
                                """))
                .andExpect(status().isConflict());
        assertEquals(1, count("alert_audit"));
    }

    private org.springframework.test.web.servlet.ResultActions submit(String key, String body) throws Exception {
        return mockMvc.perform(post("/api/payments")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String paymentId(String body) {
        Matcher matcher = Pattern.compile("\"paymentId\":\"([^\"]+)\"").matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String payload(String amount) {
        return """
                {
                  "debtorAccount": "%s",
                  "creditorAccount": "%s",
                  "amount": %s,
                  "currency": "USD",
                  "channel": "WEB",
                  "reference": "SYNTHETIC-REF-01",
                  "initiatedAt": "2026-11-14T06:30:00Z"
                }
                """.formatted(DEBTOR, CREDITOR, amount);
    }
}
