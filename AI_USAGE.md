# AI usage

- **Tool:** GitHub Copilot (AI coding agent in the IDE).
- **Key prompt:** Implement Task 1 of the supplied Project Echo assignment with H2 for local persistence: idempotent payment intake, key-reuse alerts, masked alert listing, migrations, tests, and setup documentation.
- **Review/correction:** An initial local run showed that having `flyway-core` on the classpath did not execute migrations in this Spring Boot 4 project configuration; Hibernate then failed schema validation. I used the test failure to add explicit Flyway initialization and an entity-manager dependency on that initialization, then added a migration assertion to the tests.
- **Data handling:** The examples and tests use synthetic account strings only. No credentials or real payment/customer data were provided or added.
