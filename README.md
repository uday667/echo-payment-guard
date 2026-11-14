# Project Echo — Payment Guard (Task 1)

This repository implements **Task 1** and the investigator-alert portion of **Task 3**: a REST payment intake API with database-enforced idempotency, one-time key-reuse alerts, a paginated/masked alert query, and audited alert status changes. It uses a local, file-backed **H2 database in native H2 mode**. No external database, database application, or Docker is required.

Task 2 (Kafka/Redis replay handling and stream duplicate detection) is not implemented. Docker Compose orchestration for Kafka/Redis is therefore not included.

## Requirements

- Java 17 or newer
- The Maven wrapper included in this repository (`mvnw` / `mvnw.cmd`)

No separate H2 installation is needed; H2 is included as an application dependency. Maven's offline test command works once the wrapper and project dependencies are available in the local Maven cache.

## Architecture

```text
HTTP
  -> controller
  -> intake service / transaction
       -> idempotency_keys (database unique key)
       -> payments
       -> alerts (at most one mismatch alert per key)
  -> repositories -> H2

GET /api/alerts -> newest-first paginated query -> DTO masks account values
PATCH /api/alerts/{id} -> atomic OPEN transition + alert_audit insert
GET /api/alerts/{id}/audit -> append-only review history
Flyway -> creates and versions the database schema
```

Java classes are separated under `com.echo.paymentguard`:

| Package | Responsibility |
|---|---|
| `controller` | REST routes and HTTP response mapping |
| `service` | Payment intake, idempotency, fingerprints, alert query and audited transitions |
| `repository` | Spring Data persistence interfaces and atomic alert transition |
| `model` | JPA entities, including append-only alert audit rows |
| `dto` | API request/response types |
| `config` | Flyway initialization |
| `exception` | API error handling |
| `util` | Account-number masking |

### Idempotency and transaction safety

`idempotency_keys.idempotency_key` is a primary key, so the database—not an in-memory check—serializes simultaneous attempts to reserve the same key. Payment creation and key reservation happen in one transaction. If a concurrent caller loses the unique-key race, its transaction rolls back and it looks up the winning key in a new transaction. All successful callers therefore receive the same payment ID. The mismatch alert has a unique constraint on its idempotency key, which prevents repeated or concurrent mismatches from creating multiple alerts.

The SHA-256 fingerprint is built from length-prefixed UTF-8 values for these business fields, in order: debtor account, creditor account, amount, currency, channel, reference, and `initiatedAt`. Any changed business field makes the request a mismatch. Amounts are normalized by removing insignificant trailing zeros, currency is uppercased, and `initiatedAt` is compared as an instant, so equivalent representations (for example `25.50` and `25.5000`, or different offsets for the same instant) replay consistently. Optional null fields have an unambiguous representation.

Full account values are persisted in H2 and are not included in payment responses or application-generated log messages. Alert DTOs mask every account value, showing only its last four digits (for example, `XXXXXXXX4444`). The H2 database file itself contains the persisted account values; protect that file as database data.

### Alert query index

Flyway creates `idx_alerts_status_created_at` over `(status, created_at DESC)` to serve status-filtered, newest-first alert listings. It also creates `idx_alerts_type_status_created_at` over `(type, status, created_at DESC)` to support requests filtering by both type and status, and `idx_alert_audit_alert_id_at` over `(alert_id, at)` to read one alert's audit history in order.

## H2 database

The default connection is configured in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:h2:file:./echo-payment-guard;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=validate
```

The application uses H2 directly with no PostgreSQL compatibility or external-database settings. The database is created in the repository's working directory as `echo-payment-guard.mv.db` when the service first starts. Flyway runs `V1__create_payment_and_alert_tables.sql` to create the `payments`, `idempotency_keys`, and `alerts` tables and indexes. Hibernate validates the migrated schema instead of generating or modifying it. H2 files are excluded from Git.

The database is persistent across service restarts. To start over, stop the service and remove the specific `echo-payment-guard.mv.db` file from the directory in which the service was launched; the next start recreates the schema. Do not remove the file while the service is running.

The H2 connection is configured in `src/main/resources/application.properties`. No `.env` file or database credentials need to be configured; `.env.example` is included as a placeholder for later application settings.

## Run the application

From the repository root:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS/Linux:

```sh
./mvnw spring-boot:run
```

The API listens at `http://localhost:8080`. The H2 web console is not enabled. Keep the service running in this terminal while trying the examples in another terminal. Use only synthetic data.

## REST API

### `POST /api/payments`

Required header: `Idempotency-Key` (nonblank; maximum 512 characters).

Required JSON fields: `debtorAccount`, `creditorAccount`, `amount`, `currency`, and `initiatedAt`. `channel` and `reference` are optional. Amount must be positive, currency must contain exactly three letters, and accounts must not be blank.

The three idempotency outcomes can be tried in order using the following examples. The first two use the same key and payload; the third changes the amount but retains the key.

**1. First request — `201 Created`:**

```sh
curl.exe -i http://localhost:8080/api/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: demo-payment-001" `
  -d '{"debtorAccount":"1111222233334444","creditorAccount":"5555666677778888","amount":25.50,"currency":"USD","channel":"WEB","reference":"SYNTHETIC-REF-01","initiatedAt":"2026-11-14T06:30:00Z"}'
```

On macOS/Linux, replace `curl.exe` and PowerShell backticks with `curl` and shell line continuations (`\`). Response body:

```json
{"paymentId":"<generated-uuid>","status":"ACCEPTED"}
```

**2. Same key and same payload — `200 OK`:**

Repeat the exact request above. It returns the same `paymentId`, adds `Idempotent-Replayed: true`, and does not create another payment.

**3. Same key and different payload — `409 Conflict`:**

```sh
curl.exe -i http://localhost:8080/api/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: demo-payment-001" `
  -d '{"debtorAccount":"1111222233334444","creditorAccount":"5555666677778888","amount":26.50,"currency":"USD","channel":"WEB","reference":"SYNTHETIC-REF-01","initiatedAt":"2026-11-14T06:30:00Z"}'
```

The response is a clear error body and one `KEY_REUSE_MISMATCH` alert is recorded. Repeating this changed request returns `409` without creating another alert.

Missing/blank keys and invalid JSON/body fields return `400` with an `error` message. For example, try removing the key header, or change `"amount":26.50` to `"amount":0`.

### `GET /api/alerts`

Optional query parameters: `type`, `status`, `page` (zero-based, default `0`), and `size` (default `20`, maximum `100`). Results are newest first; the response includes `content`, `page`, `size`, and `totalElements`.

```sh
curl.exe "http://localhost:8080/api/alerts?type=KEY_REUSE_MISMATCH&status=OPEN&page=0&size=20"
```

The alert response contains alert metadata and masked accounts, never the full debtor or creditor account values.

### Investigator actions

Only an `OPEN` alert can be changed to `REVIEWED` or `DISMISSED`. Any other transition returns `409 Conflict` and does not add an audit record. The status update is conditional in the database, so simultaneous actions cannot both succeed. A successful transition and its audit row are committed in the same transaction.

```powershell
curl.exe -i -X PATCH http://localhost:8080/api/alerts/<alert-id> `
  -H "Content-Type: application/json" `
  -H "X-User: investigator-01" `
  -d '{"status":"REVIEWED","note":"Validated with the vendor"}'

curl.exe http://localhost:8080/api/alerts/<alert-id>/audit
```

The audit history records `alertId`, `fromStatus`, `toStatus`, `actor`, `note`, and `at`. `X-User` supplies the actor; if omitted, the API records `investigator`. Audit rows are exposed only as a read endpoint and are never modified by status changes.

## Investigator UI

The React + TypeScript UI in `frontend/` provides type/status filters, server-side pagination, masked debtor/creditor accounts, amount/currency and creation time, and actions for open alerts. Actions send `PATCH /api/alerts/{id}` with `status`, `note`, and `X-User: investigator`; successful status changes update the displayed row without reloading the page. Errors from the API are shown in the UI.

Start the backend from the repository root:

```powershell
.\mvnw.cmd spring-boot:run
```

In another terminal, start the UI:

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL (normally `http://localhost:5173`). Vite proxies `/api` requests to `http://localhost:8080`. Create an alert using the changed-payload example above, then refresh the queue to view it and test Mark reviewed or Dismiss.

## Tests

Run the complete test suite without Docker or external services:

```powershell
.\mvnw.cmd -o test
```

On macOS/Linux:

```sh
./mvnw -o test
```

`-o` forces Maven offline mode. If Maven has never downloaded the wrapper distribution or dependencies on this machine, run `.\mvnw.cmd test` once with network access, then use the offline command. Tests use an isolated in-memory H2 database in native H2 mode; they do not read/write the local file-backed application database.

`PaymentIntakeTests` verifies:

- First request returns `201` and one accepted payment.
- Same-key, same-payload replay returns `200`, the original ID, `Idempotent-Replayed: true`, and no second payment.
- Same-key, different-payload request returns `409` and creates exactly one `KEY_REUSE_MISMATCH` alert.
- Missing idempotency key and invalid amount return `400`.
- Payment and alert API responses do not expose full synthetic account values; alert results contain masked values.
- At least 20 concurrent requests with the same key create exactly one payment and all return the same ID.
- Flyway creates the required three tables and the documented alert index.

## Scope and next work

The implemented scope is Task 1 plus the Task 3 investigator queue and audited review actions. Kafka/Redis replay detection, Docker Compose orchestration, and related integration/stream-generator tooling remain future work.
