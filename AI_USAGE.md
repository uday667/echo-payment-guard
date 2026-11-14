# AI usage and engineering review

## At a glance

| Item | Details |
|---|---|
| AI tool | GitHub Copilot, used as an interactive coding assistant |
| How it was used | Implementing and refining features, diagnosing build/test failures, and updating project documentation |
| Human responsibility | Selecting the scope, reviewing generated changes, checking behavior against requirements, and deciding what to keep |
| Data provided | Assignment requirements and synthetic examples only; no real customer data, account numbers, or credentials |

## Prompt themes

The prompts below are paraphrased from the development conversation to make the work easy to review; they are not presented as verbatim transcripts.

1. **Build the payment guard:** Implement Task 1 from the Project Echo brief, using H2 for local persistence, with idempotent REST intake, key-reuse alerts, masked alert results, database migrations, and tests.
2. **Organize the code:** Separate implementation into responsibility-based packages such as `config`, `model`, `dto`, `repository`, `service`, and `controller`.
3. **Add investigator actions:** Build the alert queue and audited review/dismiss actions, including the React interface and backend status-transition API.
4. **Implement stream processing:** Add Kafka intake through the shared payment path, Redis sliding-window detection, bounded retries/DLT handling, and synthetic stream-generation and checking scripts; update the README.
5. **Diagnose and improve reliability:** Investigate `IntakeService`, correct its event-intake/replay behavior, and address test failures around concurrent requests using the same idempotency key.
6. **Keep the handoff useful:** Review the project structure and make the README reflect the actual setup, features, tests, and known gaps.

## What AI got wrong and how it was caught

- **Migrations and startup wiring:** A local test run exposed that Flyway migrations were not being applied in the Spring Boot 4 setup, causing Hibernate schema validation to fail. The startup wiring was corrected and migration behavior was covered by a test.
- **Concurrency:** An initial implementation allowed concurrent requests with the same key to race, creating inconsistent results. The failing concurrency test exposed this; same-key requests were then coordinated in-process, while the database uniqueness constraint remained the persistence-level guard.
- **Event intake structure:** Review of `IntakeService` found event methods nested incorrectly inside the REST intake method. They were moved to the appropriate class scope and replay handling was corrected.
- **Documentation claims:** The README initially contained stale or duplicate instructions. It was revised to distinguish implemented behavior from missing requirements and unmeasured results.

## Verification performed

- Backend: `.\mvnw.cmd -o -q test` passed after the concurrency and compilation fixes (7 tests reported).
- Frontend: `npm.cmd test` passed from `frontend/` (2 tests reported).
- Task 2 Redis-window unit tests, Kafka/Redis integration tests, and a 10,000-event throughput run were **not** completed. See the README's scope and remaining-work notes; no results are claimed for them.

## Interview review guide

The most useful areas to inspect and discuss are:

- `IntakeService` and the idempotency/payment database constraints: how retries, payload mismatches, and concurrent requests are handled.
- `PaymentStreamService` and `PaymentWindowService`: how events share intake logic and how Redis tracks event-time windows.
- `AlertWorkflowService` and the alert controller: how allowed status transitions and append-only audit entries are persisted.
- `PaymentIntakeTests` and `frontend/src/App.test.tsx`: the behavior covered by the offline backend and UI tests.
- The README's **Scope and known gaps** section: the differences between the delivered implementation and the full assignment requirements.

AI-generated code was treated as a draft, not as proof of correctness. The implementation and its limitations should be explained from the code and tests during review.
