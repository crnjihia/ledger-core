# Release Notes: Ledger Core v1.0.0

**Release Tag**: `v1.0.0`  
**Date**: October 16, 2026  
**Target Environment**: Production / Cloud-Native Kubernetes / Docker  

---

## 🌟 Overview

**Ledger Core v1.0.0** is the inaugural production release of my mission-critical, double-entry core banking engine, modeled on Kenya's Real-Time Gross Settlement (RTGS) system and Safaricom M-Pesa B2C settlement flows.

Engineered with zero-trust banking standards, strict ACID transactional semantics under concurrent high-volume stress, and full regulatory auditability.

---

## 🚀 Key Features & Capabilities

### 1. Immutable Double-Entry Ledger Engine
* **Balanced Legs**: Every transaction requires balanced DEBIT and CREDIT entries adhering to the invariant:
  $$\sum \text{Debits} = \sum \text{Credits} = \text{Transaction Amount}$$
* **Running Balances**: Stored per entry for instant statement generation and tamper evidence.
* **Database Immutability Triggers**: Implemented via PostgreSQL PL/pgSQL triggers (`block_ledger_entries_modifications`). `UPDATE` and `DELETE` queries on `ledger_entries` throw fatal exceptions.
* **Constraints**: Database check constraints (`amount > 0`, `balance >= 0`).

### 2. Deadlock-Free RTGS Fund Transfers
* **Ordered Pessimistic Locking**: Eliminates deadlocks during opposing concurrent transfers ($A \rightarrow B$ and $B \rightarrow A$) by sorting account IDs in deterministic ascending alphanumeric order (`min(fromId, toId), max(fromId, toId)`) before acquiring `SELECT ... FOR UPDATE` row locks.
* **ACID Isolation**: All transfer methods execute under `@Transactional(isolation = Isolation.REPEATABLE_READ)`.
* **Zero Lost Updates**: Verified under 50-thread concurrent stress testing racing on limited account funds.

### 3. Mobile Money Disbursements (M-Pesa B2C Settlement & Reversals)
* **Clearing Suspense Account**: Segregates disbursed funds into a dedicated `SETTLEMENT` suspense account prior to external network dispatch.
* **Automated Gateway Retries & Auto-Reversal**:
  - Immediate synchronous provider failures trigger an automatic atomic reversal transaction (`ledgerService.reverse(...)`), refunding customer funds immediately.
  - Gateway timeouts transition status to `SENT` and await asynchronous callback processing via webhook (`POST /api/v1/disbursements/mpesa/callback`).
* **Velocity Limits**: Per-transaction ceiling (KES 250,000) and daily rolling volume caps (KES 1,000,000) enforced at the service boundary.

### 4. Multi-Tier Idempotency Engine
* **Hybrid Storage**:
  - **Redis 7 Fast-Path**: 24-hour TTL cache returning sub-millisecond responses on repeated calls with zero database hit.
  - **PostgreSQL Durable Storage**: Permanent `idempotency_records` table ensuring transactional durability.
* **Payload Mismatch Rejection**: Reusing an existing idempotency key with an altered payload hash is immediately rejected with **`HTTP 409 Conflict`**.

### 5. Bank-Grade Security & Authentication
* **Dual-Token JWT**:
  - Short-lived Access Tokens (15 minutes, HMAC-SHA256).
  - Long-lived Refresh Tokens (7 days, cryptographically random, rotated upon every refresh and stored in Redis).
* **Role-Based Access Control (RBAC)**: Strict method-level security (`@PreAuthorize`) enforcing boundaries for `CUSTOMER`, `TELLER`, `ADMIN`, and `AUDITOR`.
* **Token Bucket Rate Limiting (Bucket4j + Redis)**:
  - 10 transfers / minute per authenticated user.
  - 100 read requests / minute per authenticated user.
  - Returns `HTTP 429 Too Many Requests` with `Retry-After` header.
* **BCrypt Password Hashing**: Work factor (cost) 12.

### 6. Scheduled Nightly Reconciliation Job
* Automated nightly job (Spring `@Scheduled`) scanning the ledger to verify that global debits match global credits:
  $$\sum \text{Credits} - \sum \text{Debits} = 0$$
* Emits structured audit events on completion.

### 7. Full Observability & Audit Trail
* **Asynchronous Dual-Publishing**: State changes produce detailed audit events with correlation ID, actor username, IP address, and before/after JSON state diffs.
* **ELK Stack Ready**: Direct Logstash TCP pipeline delivering structured JSON logs to Elasticsearch 8.13 and Kibana dashboards.
* **Micrometer & Prometheus**: Metrics for transfer volume, latency timers, lock wait times, and idempotency hits.
* **Custom Health Indicators**: Live health status for database connectivity, Redis ping, and M-Pesa gateway reachability.

---

## 📦 Deliverables & Verification

- **Docker Compose Stack**: [`docker-compose.yml`](docker-compose.yml) deploying PostgreSQL 15, Redis 7, Elasticsearch, Logstash, Kibana, and the Spring Boot application container.
- **Flyway Migrations**: Incremental migrations `V1__` through `V9__` covering schemas, triggers, and constraints.
- **OpenAPI 3.0 / Swagger UI**: Available at `/swagger-ui/index.html` with Bearer auth support.
- **Postman Test Suite**: [`postman_collection.json`](postman_collection.json) containing 7 modules with automated test assertions.
- **Test Suite**: 19 out of 19 automated integration and concurrency tests passing with Testcontainers (`mvn clean test`).

---

## 🛠️ Verification Command

```bash
# Verify build and test suite
./mvnw clean test

# Run turnkey environment
docker compose up -d
```
