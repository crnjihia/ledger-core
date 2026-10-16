# Security Policy and Architecture

## Overview
Ledger Core is architected as a mission-critical, zero-trust financial ledger designed according to international banking security standards and Kenyan regulatory requirements (CBK Guidelines on Cybersecurity). 

This document details the authentication and authorization model, data integrity protections, cryptography standards, concurrency and race-condition mitigations, and threat models.

---

## 1. Authentication & Session Management

### Dual-Token JWT Architecture
- **Access Tokens**: Short-lived (15 minutes), signed with HMAC-SHA256 (`HS256`) using a cryptographically secure 256-bit key. Stateless verification on every protected request.
- **Refresh Tokens**: Long-lived (7 days), cryptographically random UUIDs stored in Redis (`refresh_token:<token>`) with automatic TTL expiration.
- **Token Rotation & Invalidation**:
  - Every call to `/auth/refresh` immediately revokes the old refresh token and issues a new pair (Refresh Token Rotation - RTR).
  - Explicit logout (`/auth/logout`) invalidates the refresh token immediately from Redis.
  - Replay of an invalidated refresh token is prevented by immediate key deletion.

### Password Hashing
- Passwords are encrypted using **BCrypt** with work factor (cost) **12**:
  ```java
  @Bean
  public PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(12);
  }
  ```
- Protects against offline rainbow-table and GPU-accelerated dictionary attacks.

---

## 2. Role-Based Access Control (RBAC)

Ledger Core enforces strict principle of least privilege using Spring Security 6 and method-level authorization (`@EnableMethodSecurity`):

| Role | Permissions & Scope |
| :--- | :--- |
| `CUSTOMER` | View own accounts, balances, and paginated transaction statements; initiate transfers and disbursements from own accounts. |
| `TELLER` | Create and activate accounts; view balances and transactions for customer service operations; initiate deposits and withdrawals. |
| `AUDITOR` | Read-only global visibility: query full ledger entries, transaction histories, reconciliation logs, and audit logs. Forbidden from executing state-changing transactions. |
| `ADMIN` | System configuration, user account provisioning, role assignment, manual reconciliation triggers, and parameter management. |

---

## 3. Defense Against Financial & Transactional Threats

### Double-Entry Integrity & Immutability
- **Mathematical Invariant**: Every transfer produces exactly 2 ledger entries (1 DEBIT and 1 CREDIT) summing to zero:
  $$\sum \text{Debits} = \sum \text{Credits}$$
- **Database Immutability Trigger**: A PostgreSQL trigger blocks any `UPDATE` or `DELETE` statement on `ledger_entries`:
  ```sql
  CREATE TRIGGER trg_ledger_entries_immutability
  BEFORE UPDATE OR DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION prevent_ledger_modification();
  ```
- **Positive Amount Constraint**: Database check constraint `amount > 0` ensures negative balances or inverse entries cannot be forged.
- **Account Balance Check**: DB-level check `balance >= 0` prevents unapproved overdrafts.

### Concurrency & Deadlock-Free Pessimistic Locking
- To eliminate race conditions, double-spending, and balance corruption during high concurrent volume, transfer execution acquires row-level pessimistic locks (`SELECT ... FOR UPDATE`).
- **Deterministic Ordering**: Accounts are always locked in ascending order of their IDs (`min(fromId, toId)`, then `max(fromId, toId)`). This mathematically proves the elimination of circular lock dependencies (deadlocks).
- **Isolation Level**: Transfer methods execute under `@Transactional(isolation = Isolation.REPEATABLE_READ)`.

### Multi-Tier Idempotency Engine
- Intercepts state-changing endpoints (`/transfers`, `/disbursements`, etc.) using the `Idempotency-Key` HTTP header.
- **Redis Cache (Fast-Path)**: Cached for 24 hours. Replay of an existing valid key returns the exact cached response with zero DB write overhead.
- **PostgreSQL Durable Store**: Permanent record of idempotency keys, request hashes, and responses.
- **Payload Conflict Detection**: If a request uses an existing idempotency key with a different payload body, the system rejects it with **`HTTP 409 Conflict`**.

### Rate Limiting & DDoS Protection
- Distributed token-bucket algorithm using **Bucket4j** backed by **Redis**:
  - **Transfers & Disbursements**: Strict limit of **10 requests/minute** per authenticated user.
  - **Read / Query Endpoints**: Limit of **100 requests/minute** per authenticated user.
  - Anonymous IP fallback protection for public authentication endpoints.
  - Excessive requests return `HTTP 429 Too Many Requests` with `Retry-After` header.

---

## 4. Comprehensive Audit Trail & Observability

- **Spring AOP `@Auditable` Interception**: Captures actor ID, action type, entity ID, source IP address, user-agent, correlation ID, and JSON snapshots of before/after state diffs.
- **Dual Persistence**:
  1. Relational persistence in PostgreSQL `audit_logs` table for compliance queries (`GET /audit-logs`).
  2. Asynchronous structured JSON streaming via Logback to **Logstash -> Elasticsearch -> Kibana** for SIEM monitoring.
- **Correlation & MDC Tracing**: Every HTTP request receives an `X-Correlation-ID` header tracked across thread contexts and logged in all output streams.

---

## 5. Security Vulnerability Reporting

If you discover a security vulnerability within Ledger Core, please report it privately:
- **Email**: security@ledgercore.ke
- Please include:
  - Description of vulnerability and impact
  - Step-by-step reproduction instructions or proof of concept (PoC)
  - Suggested remediation if available
- Please do **not** open public GitHub issues for security vulnerabilities.
