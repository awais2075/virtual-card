# Virtual Card Issuance Platform

A Spring Boot REST API for virtual card issuance, top-ups, spending, and transaction tracking.
---

## Table of Contents

- [Getting Started](#getting-started)
- [Running Locally](#running-locally)
- [API Overview](#api-overview)
- [Design Decisions](#design-decisions)
- [Observability](#observability)
- [Testing Strategy](#testing-strategy)
- [Known Gaps & Trade-offs](#known-gaps--trade-offs)
- [Future Improvements](#future-improvements)
- [Scaling & Architecture Evolution](#scaling--architecture-evolution)

---

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for running PostgreSQL and integration tests via Testcontainers)

### Running Locally

1. Start a PostgreSQL instance:

```bash
docker run --name vcard-postgres \
  -e POSTGRES_DB=vvcard \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=virtual-card \
  -p 5432:5432 \
  -d postgres:15-alpine
```

2. Run the application:

```bash
./mvnw spring-boot:run
```

3. Access the Swagger UI:

```
http://localhost:8080/swagger-ui
```

4. Access the Actuator health endpoint:

```
http://localhost:8080/actuator/health
```

### Running Tests

```bash
./mvnw test
```

Integration tests use Testcontainers and spin up a real PostgreSQL instance automatically — no manual setup required.

---

## API Overview

All endpoints are prefixed with `/api/v1/cards`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/` | Create a new virtual card |
| `GET` | `/{cardId}` | Get card details and current balance |
| `POST` | `/{cardId}/credit` | Top up a card |
| `POST` | `/{cardId}/debit` | Spend from a card |
| `GET` | `/{cardId}/transactions` | Paginated transaction history |

### Example: Create a Card

```bash
curl -X POST http://localhost:8080/api/v1/cards \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "initBalance": 100.00}'
```

### Example: Top Up

```bash
curl -X POST http://localhost:8080/api/v1/cards/{cardId}/credit \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00, "idempotencyKey": "topup-uuid-001"}'
```

### Example: Spend

```bash
curl -X POST http://localhost:8080/api/v1/cards/{cardId}/debit \
  -H "Content-Type: application/json" \
  -d '{"amount": 30.00, "idempotencyKey": "spend-uuid-001"}'
```

---

## Design Decisions

### One Active Card Per Cardholder
A partial unique index (`uidx_card_active_cardholder`) ensures a cardholder can only hold one `ACTIVE` card at a time. Because it is a partial index scoped to `status = 'ACTIVE'`, the same cardholder can still have historical `CLOSED` or `EXPIRED` cards without violating the constraint. All database scripts can be found in `db.sql`.

### Domain Modelling

The `Card` entity carries both a `CardStatus` (coarse-grained: `ACTIVE`, `BLOCKED`, `CLOSED`, `EXPIRED`) and a `CardSubStatus` (fine-grained: `IN_USE`, `FROZEN`, `LOST`, `STOLEN`, etc.). This two-tier status model allows the API to evolve — for example, a `FROZEN` card is still `ACTIVE` at the status level, keeping queries simple (`WHERE status = 'ACTIVE'`), while the sub-status carries the operational nuance. Adding new sub-states does not require schema changes to the primary status column.

### Financial Precision

All monetary values use `BigDecimal` with `precision=19, scale=4` at the database level. This avoids the rounding errors inherent to `float` and `double`, which is critical for financial arithmetic. Equality checks in tests use `isEqualByComparingTo` rather than `isEqualTo` to be scale-insensitive (`100` vs `100.0000`).

### Layered Architecture

The codebase follows a strict layered structure: Controller → Service → Repository. DTOs (`CardRequest`, `CardResponse`, `TransactionRequest`, `TransactionResponse`) are used at the controller boundary; domain entities (`Card`, `Transaction`) never escape the service layer. Mappers (`CardMapper`, `TransactionMapper`) handle the translation.

### Audit Trail

All entities extend `AuditMetadata`, which uses Spring Data JPA auditing to automatically populate `createdAt`, `updatedAt`, `createdBy`, and `updatedBy`. Declined transactions are written to the database before the `InsufficientFundsException` is thrown, ensuring a complete audit trail even on failed operations.

### Card Expiry Scheduler

A scheduled job (`CardExpiryScheduler`) runs daily at midnight and transitions any `ACTIVE` cards past their expiry date to `EXPIRED` status with sub-status `EXPIRED_NATURAL`. The cron expression is externalised to `card.expiry.cron` in `application.yml` so it can be overridden per environment without a code change.

---

## Observability

### Metrics

Micrometer counters are incremented at key business events:

| Metric | Description |
|--------|-------------|
| `cards.created` | Card created successfully |
| `transactions.credit.success` | Credit applied |
| `transactions.debit.success` | Debit applied |
| `transactions.debit.declined` | Debit declined (insufficient funds) |
| `transactions.idempotency.hit` | Duplicate request detected |

These are exposed via Spring Actuator at `/actuator/metrics` and can be scraped by Prometheus.

### Logging

Structured logging is in place at all service entry and exit points using SLF4J. Credit and debit operations log the card ID and resulting balance. Errors (card not found, insufficient funds, unexpected exceptions) are logged at `ERROR` level with relevant context.

### Actuator

The full Actuator endpoint suite is enabled (`management.endpoints.web.exposure.include=*`), including `/actuator/health`, `/actuator/metrics`, `/actuator/info`, and `/actuator/prometheus`.

---

## Testing Strategy

### Unit Tests (`CardServiceImplTest`, `TransactionServiceImplTest`)

Pure unit tests using Mockito. Every branch in the service layer is covered: happy paths, duplicate idempotency keys, insufficient funds, frozen cards, zero/negative amounts, BigDecimal precision edge cases. Mockito `ArgumentCaptor` is used to assert on the exact state of entities passed to the repository, rather than trusting mock return values.

### Integration Tests (`CardIntegrationTest`)

Full-stack tests using `MockMvc` against a real PostgreSQL instance spun up by Testcontainers. Each test exercises the complete HTTP request/response cycle, including serialisation, validation, and database persistence. Tests cover the full lifecycle (create → credit → debit → balance check), idempotency replay, duplicate card rejection, 404 handling, and validation error responses.

The integration test suite is intentionally lean — each test asserts only on the final observable state (the HTTP response), not on intermediate steps. This keeps tests resilient to internal refactoring.

---

## Known Gaps & Trade-offs

### No block/freeze/close Card Endpoints 
The domain model and exception classes are fully prepared for these operations, but no API endpoints were implemented within the time constraint. The `CardFrozenException` is guarded against in the service layer, but there is no way for a caller to actually freeze a card.

### No Foreign Key Constraint on Transaction Table

No Foreign Key from Transaction to Card.** `transaction.card_id` references `card.id` at the application level only — there is no database foreign key constraint. This is a common pattern in high-volume financial systems where the `transaction` table is the hottest table in the database and a foreign key would add a referential integrity check on every insert. At scale this overhead adds up. There is also a forward-looking reason — if the `transaction` table is ever partitioned by `card_id` or date range, foreign keys become a structural problem since the referenced row may live on a different partition or node, and most distributed databases do not support cross-shard foreign keys at all. Since cards are never deleted in this system (only transitioned to `CLOSED` or `EXPIRED`), orphaned transactions cannot occur in practice, so the application-level enforcement in `CardServiceImpl` is sufficient.


### Rate Limiting

Rate limiting should be handled at the API Gateway level (e.g. Azure API Management, AWS API Gateway, Kong, or NGINX) rather than within the application itself. Embedding a library like Bucket4j adds operational overhead and state management complexity that is better owned by the infrastructure layer, which is already responsible for traffic control in a cloud deployment.

---

## Future Improvements

### Card Creation / Replacement Flow
Should have a Business Process Model (BPM) like Flowable, jBPM or Camunda for Card Creation and Replacement where we can use all the CardStatus and CardSubstatus

### Block/freeze/close Card Endpoints

Add `PATCH /api/v1/cards/{cardId}/status` to allow status transitions, with validation of legal transitions (e.g. `ACTIVE → BLOCKED`, `BLOCKED → ACTIVE`, `ACTIVE → CLOSED`).

---

## Scaling & Architecture Evolution

### Moving to Microservices

The codebase is structured to make extraction straightforward. A natural first split would be:

- **Card Service** — owns card lifecycle (create, status management, balance reads)
- **Transaction Service** — owns transaction recording, history, and idempotency

Communication between them could use synchronous REST for the critical path (balance updates) and asynchronous events (Kafka) for non-critical flows (audit, analytics, notifications).

### Event-Driven Architecture

Each financial operation produces a business event (`CardCreated`, `TransactionApplied`, `TransactionDeclined`). Publishing these to Kafka allows downstream consumers to build read models, trigger notifications, feed fraud detection systems, and populate analytics pipelines — all without coupling to the core transaction path.


---

### Scheduler Observability

The `CardExpiryScheduler` currently logs how many cards it expired, but emits no metrics. Add a `cards.expired` Micrometer counter and a gauge for scheduler execution duration so anomalies (e.g. the job taking unusually long) are detectable.

---

## Learning Strategy

### Testcontainers

Prior to this project, integration testing with real databases typically involved either an embedded H2 database (fast but dialect-inconsistent). Testcontainers was explored by reading the official documentation and the Spring Boot Testcontainers integration guide.
### Pessimistic Locking with Spring Data JPA

Applying `@Lock(LockModeType.PESSIMISTIC_WRITE)` as Credit and Debit operations acquire a PESSIMISTIC_WRITE lock on the card row at read time, ensuring that concurrent transactions on the same card are serialised — preventing one transaction from reading a stale balance while another is mid-update.
### Micrometer Counters

Micrometer's API was unfamiliar at the start. The learning approach was to look at a single working example — incrementing a counter on card creation — and then replicate the pattern for the remaining business events. The `MeterRegistry.counter(name).increment()` pattern is intentionally simple.