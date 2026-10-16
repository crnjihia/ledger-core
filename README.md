# Ledger Core

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%2F3.4-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg?style=flat&logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg?style=flat&logo=redis)](https://redis.io/)
[![ELK Stack](https://img.shields.io/badge/ELK-8.13-lightgrey.svg?style=flat&logo=elastic)](https://www.elastic.co/elk-stack)
[![Docker Compose](https://img.shields.io/badge/Docker-Ready-2496ED.svg?style=flat&logo=docker)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> **Production-grade core banking transaction processing engine modelled on Kenya's Real-Time Gross Settlement (RTGS) and M-Pesa mobile money flows.**
> 
> Engineered for strict ACID correctness, mathematical double-entry invariant verification, deadlock-free pessimistic locking under high concurrent load, and multi-tier idempotency guarantees.

---

## Architecture Overview

```mermaid
flowchart TD
    Client([Client / Mobile App / Gateway])

    subgraph SecurityGateway [Security & Ingress]
        RateLimit["RateLimiter Filter\n(Bucket4j + Redis)"]
        JwtFilter["JWT Authentication Filter\n(Stateless + RBAC)"]
        TraceFilter["Request ID / MDC Filter\n(Traceability)"]
    end

    subgraph CoreApplication [Ledger Core Engine]
        AuthController["AuthController"]
        AccountController["AccountController"]
        TransferController["TransferController"]
        DisbursementController["DisbursementController"]
        LedgerController["LedgerController"]
        
        IdempService["IdempotencyService\n(Redis Fast-Path + DB Claim)"]
        TransferService["TransferService\n(Deterministic Locking)"]
        LedgerService["LedgerService\n(Double-Entry Engine)"]
        DisbService["DisbursementService\n(B2C Settlement)"]
    end

    subgraph Datastores [Storage & State Tier]
        PostgreSQL[("PostgreSQL 16\nACID Ledger & Triggers")]
        RedisCache[("Redis 7\nIdempotency & Rate Limits")]
    end

    subgraph Observability [Telemetry & Audit Tier]
        Logstash["Logstash Ingest"]
        Elasticsearch[("Elasticsearch 8.13")]
        Kibana["Kibana Audit Dashboard"]
    end

    Client --> TraceFilter --> RateLimit --> JwtFilter
    JwtFilter --> AuthController
    JwtFilter --> AccountController
    JwtFilter --> TransferController
    JwtFilter --> DisbursementController
    JwtFilter --> LedgerController

    TransferController --> IdempService --> TransferService
    DisbursementController --> IdempService --> DisbService
    TransferService --> LedgerService
    DisbService --> LedgerService

    LedgerService --> PostgreSQL
    IdempService --> RedisCache
    CoreApplication -.-> Logstash --> Elasticsearch --> Kibana
```

---

## Core Financial Guarantees

### 1. Mathematical Double-Entry Invariant
Every financial transaction is recorded as balanced pairs of debit and credit legs. No money is ever created or destroyed in transit:
$$\sum \text{Debit Amounts} = \sum \text{Credit Amounts} = \text{Transaction Amount}$$

* **Debit**: Deducts from an asset account (e.g. sender's balance).
* **Credit**: Increments a liability/deposit account (e.g. receiver's balance).
* **DB-Level Immutability**: Enforced via PostgreSQL triggers. Any direct `UPDATE` or `DELETE` executed on `ledger_entries` throws a fatal SQLSTATE exception. Auditing is strictly append-only.

### 2. Deadlock-Free Deterministic Locking (RTGS Transfer Flow)
To eliminate database deadlocks when two accounts simultaneously transfer funds to each other ($A \rightarrow B$ and $B \rightarrow A$), Ledger Core sorts account IDs deterministically in ascending alphanumeric order before issuing a `SELECT ... FOR UPDATE`:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client / Core Channel
    participant Filter as RateLimit & Auth Filter
    participant Idemp as IdempotencyService
    participant Redis as Redis (Cache / Locks)
    participant Xfer as TransferService
    participant DB as PostgreSQL (ACID)
    participant Ledger as LedgerService
    participant Audit as Async Audit (ELK/DB)

    Client->>Filter: POST /api/v1/transfers (Bearer JWT, Idempotency-Key)
    Filter->>Filter: Bucket4j Token Bucket Check & JWT verification
    Filter->>Idemp: Delegate transfer request
    Idemp->>Redis: GET idempotency:<key>
    alt Fast-Path Cache Hit
        Redis-->>Client: 200 OK (Cached response payload)
    else Cache Miss
        Idemp->>DB: Atomic claim on idempotency_records
        Idemp->>Xfer: createTransfer(request)
        Note over Xfer, DB: Deterministic Lock Acquisition (Ordered by accountId ASC)
        Xfer->>DB: SELECT * FROM accounts WHERE id IN (accA, accB) ORDER BY id ASC FOR UPDATE
        DB-->>Xfer: Pessimistic Write Locks Granted
        Xfer->>Xfer: Verify source balance >= amount + fee
        Xfer->>Ledger: post(debit: accA, credit: accB, amount)
        Ledger->>DB: INSERT INTO ledger_transactions (status: POSTED)
        Ledger->>DB: INSERT INTO ledger_entries (DEBIT accA, runningBalance)
        Ledger->>DB: INSERT INTO ledger_entries (CREDIT accB, runningBalance)
        Ledger->>DB: UPDATE accounts (balance = balance - debit / balance + credit)
        Xfer->>DB: INSERT INTO transfers (status: COMPLETED)
        Xfer->>Audit: Publish AuditEvent async (PostgreSQL + Logstash/Elasticsearch)
        Xfer-->>Idemp: TransferResponse (id, amount, status: COMPLETED)
        Idemp->>DB: UPDATE idempotency_records SET completed=true, response=payload
        Idemp->>Redis: SETEX idempotency:<key> 86400 (Hash + Status + Response)
        Idemp-->>Client: 200 OK (TransferResponse)
    end
```

### 3. Mobile Money Disbursement Flow (M-Pesa B2C Settlement & Reversals)
Modeled on Safaricom M-Pesa B2C settlement flows using a suspense/settlement clearing account, automated gateway retries, timeout management, and asynchronous callback reconciliation:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Initiator / Disbursing System
    participant Disb as DisbursementService
    participant Ledger as LedgerService
    participant DB as PostgreSQL Ledger
    participant Gateway as Safaricom M-Pesa Gateway (Daraja)
    participant Callback as M-Pesa Callback Webhook

    Client->>Disb: POST /api/v1/disbursements (MSISDN, amount, remarks)
    Disb->>Disb: Validate limits (<= 250k/txn, <= 1M/day)
    Disb->>Ledger: post(debit: sourceAccount, credit: SETTLEMENT_SUSPENSE)
    Ledger->>DB: Record double-entry legs (funds moved to suspense)
    Disb->>DB: INSERT INTO disbursements (status: INITIATED)

    alt Scenario A: Synchronous Success
        Disb->>Gateway: POST /b2c/v1/paymentrequest
        Gateway-->>Disb: 200 OK (Receipt: REC123456)
        Disb->>DB: UPDATE disbursements (status: CONFIRMED, receipt: REC123456)
        Disb-->>Client: 200 OK (Status: CONFIRMED, Receipt: REC123456)
    else Scenario B: Synchronous Provider Failure
        Disb->>Gateway: POST /b2c/v1/paymentrequest
        Gateway-->>Disb: 400/500 Failure ("Insufficient provider funds")
        Disb->>Ledger: reverse(disbursementTxId, "Gateway failure")
        Ledger->>DB: Auto-reverse: debit SETTLEMENT, credit sourceAccount (Refund)
        Disb->>DB: UPDATE disbursements (status: FAILED)
        Disb-->>Client: 200 OK (Status: FAILED, Reason: Provider failure)
    else Scenario C: Gateway Timeout & Asynchronous Callback
        Disb->>Gateway: POST /b2c/v1/paymentrequest (Times out)
        Disb->>DB: UPDATE disbursements (status: SENT, awaiting callback)
        Disb-->>Client: 200 OK (Status: SENT)
        Note over Gateway, Callback: M-Pesa asynchronous settlement processing...
        Gateway->>Callback: POST /api/v1/disbursements/mpesa/callback (ResultCode, Receipt)
        alt Callback SUCCESS
            Callback->>DB: UPDATE disbursements (status: CONFIRMED, receipt: CALLBACKREC123)
        else Callback FAILURE
            Callback->>Ledger: reverse(disbursementTxId, "Callback failure")
            Ledger->>DB: Auto-reverse funds back to source account
            Callback->>DB: UPDATE disbursements (status: FAILED)
        end
        Callback-->>Gateway: 200 OK (Webhook ACK)
    end
```

### 4. Multi-Tier Idempotency Engine
Every state-changing money movement requires an `Idempotency-Key` header with payload hash validation:

```mermaid
sequenceDiagram
    autonumber
    Client->>API: POST /transfers (Idempotency-Key: K, Payload: P)
    API->>API: Compute SHA-256 hash of request payload
    API->>Redis: GET idempotency:K
    alt Redis Cache Hit
        API->>API: Verify cachedHash == requestHash
        alt Same Key + Same Hash
            Redis-->>API: Return cached status & payload
            API-->>Client: 200 OK (from cache, zero DB hit)
        else Same Key + Different Hash
            API-->>Client: 409 Conflict ("Key reuse with different payload")
        end
    else Redis Cache Miss
        API->>DB: INSERT INTO idempotency_records (key, hash) ON CONFLICT DO NOTHING
        alt Conflict on Insert (Duplicate Key in DB)
            API->>DB: SELECT * FROM idempotency_records WHERE key = K
            alt Same Hash & Completed
                DB-->>API: Return stored response
                API->>Redis: Repopulate Redis cache (TTL 24h)
                API-->>Client: 200 OK
            else Different Hash
                API-->>Client: 409 Conflict
            end
        else Claim Acquired (First Request)
            API->>Core: Execute transaction atomically
            API->>DB: UPDATE idempotency_records SET completed=true, response=payload
            API->>Redis: SETEX idempotency:K 86400 (Hash + Status + Response)
            API-->>Client: 200 OK / 201 Created
        end
    end
```

---

## Tech Stack & Architecture

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Language** | Java 21 LTS | Pattern matching, virtual threads ready, modern records |
| **Framework** | Spring Boot 3.3 / 3.4 | Cloud-native microservice architecture, Spring Security 6, Data JPA |
| **Database** | PostgreSQL 16 | ACID transactions, row-level pessimistic locking, trigger-based immutability |
| **Migrations** | Flyway | Versioned database schema evolution (V1 through V8) |
| **Caching / Locks** | Redis 7 | Sub-millisecond idempotency deduplication and token bucket rate limiting |
| **Rate Limiter** | Bucket4j 8.10 + Redis | Per-IP and per-account burst control and DDoS mitigation |
| **Audit & Logs** | ELK Stack 8.13 | Structured JSON logging with Logback socket appender to Logstash |
| **Testing** | JUnit 5, Mockito, Testcontainers | Real ephemeral PostgreSQL and Redis instances for stress & concurrency testing |
| **Documentation** | OpenAPI 3.0 / Swagger UI | Interactive API documentation with JWT authentication integration |

---

## Project Structure

```text
ledger-core/
├── .github/workflows/ci.yml       # Automated GitHub Actions CI pipeline
├── docker/
│   ├── logstash/pipeline/        # Logstash pipeline configuration
│   └── ...
├── src/
│   ├── main/
│   │   ├── java/ke/ledgercore/
│   │   │   ├── config/           # OpenAPI and Bean configurations
│   │   │   ├── controller/       # REST API endpoints (Auth, Accounts, Transfers, etc.)
│   │   │   ├── dto/              # Request/Response data transfer objects
│   │   │   ├── exception/        # Global exception handling & custom domain errors
│   │   │   ├── model/            # JPA entities (Account, LedgerEntry, Transfer, etc.)
│   │   │   ├── rate/             # Bucket4j Redis-backed RateLimitFilter
│   │   │   ├── repository/       # Spring Data JPA repositories with pessimistic locks
│   │   │   ├── security/         # JWT filters, UserDetailsService, SecurityConfig
│   │   │   └── service/          # Business logic, double-entry engine, M-Pesa gateway
│   │   └── resources/
│   │       ├── db/migration/     # Flyway SQL migrations (V1 to V8)
│   │       ├── application.yml   # Default local development profile
│   │       └── application-docker.yml # Containerized network profile
│   └── test/java/ke/ledgercore/  # Testcontainers concurrency & integration suites
├── docker-compose.yml            # PostgreSQL, Redis, Elasticsearch, Logstash, Kibana, App
├── Dockerfile                    # Multi-stage production build container
├── Makefile                      # Standardized build and operational targets
├── pom.xml                       # Maven build descriptor with Java 21 & JaCoCo rules
└── README.md
```

---

## Quickstart

### Prerequisites
* **Java 21** (Eclipse Temurin 21 recommended)
* **Docker & Docker Compose** (running via Docker Desktop or daemon)

### 1. Clone & Configuration
```bash
git clone https://github.com/your-username/ledger-core.git
cd ledger-core
cp .env.example .env
```

### 2. Start Supporting Infrastructure
Spin up PostgreSQL and Redis:
```bash
make docker-up
# Or: docker compose up -d postgres redis
```

### 3. Build & Run Application Locally
Run with the bundled Maven wrapper:
```bash
# On Linux/macOS
./mvnw spring-boot:run

# On Windows PowerShell
.\mvnw.cmd spring-boot:run
```

The application will start on port `8080`.
* **API Documentation**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **Health Check**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

### Turnkey Full-Stack Docker Deployment
To launch the complete cluster (API + Postgres + Redis + Elasticsearch + Logstash + Kibana):
```bash
docker compose --profile docker up -d --build
```

---

## Interactive API Walkthrough (cURL)

A complete automated demonstration script is available in [`scripts/demo.sh`](file:///c:/Users/crnji/Videos/Projects/Java/ledger-core/scripts/demo.sh). You can also test the endpoints manually:

### 1. Authenticate & Obtain JWT
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

echo "JWT: $TOKEN"
```

### 2. Create Accounts
```bash
# Get Admin User ID
USER_ID=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/auth/me | jq -r '.userId')

# Create Account 1
ACC1_ID=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"userId\": \"$USER_ID\"}" | jq -r '.id')

# Create Account 2
ACC2_ID=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"userId\": \"$USER_ID\"}" | jq -r '.id')
```

### 3. Initial Ledger Deposit (Credit Account 1 with KES 1,000)
```bash
curl -i -X POST http://localhost:8080/api/v1/ledger/post \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{
    \"entries\": [
      {\"accountId\":\"$ACC1_ID\", \"amount\": 1000, \"entryType\": \"CREDIT\"}
    ]
  }"
```

### 4. Idempotent Transfer (Account 1 -> Account 2)
```bash
TRANSFER_KEY=$(uuidgen)

# First Request: Transfer executes and debits $ACC1 / credits $ACC2
curl -i -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $TRANSFER_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"fromAccountId\":\"$ACC1_ID\",
    \"toAccountId\":\"$ACC2_ID\",
    \"amount\": 250,
    \"description\": \"Internal account transfer\"
  }"

# Second Request (Replay): Returns identical cached response, prevents double-spend
curl -i -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $TRANSFER_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"fromAccountId\":\"$ACC1_ID\",
    \"toAccountId\":\"$ACC2_ID\",
    \"amount\": 250,
    \"description\": \"Internal account transfer\"
  }"
```

### 5. Mobile Money B2C Disbursement
```bash
curl -i -X POST http://localhost:8080/api/v1/disbursements \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{
    \"fromAccountId\":\"$ACC1_ID\",
    \"msisdn\":\"+254712345678\",
    \"amount\": 150,
    \"remarks\":\"Utility refund\"
  }"
```

### 6. Query Paginated Statement with Running Balance
```bash
curl -s -X GET "http://localhost:8080/api/v1/accounts/$ACC1_ID/statement?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## Testing & Quality Assurance

Ledger Core adheres to the testing pyramid with automated unit, concurrency, and containerized integration tests.

```bash
# Run unit and validation tests
./mvnw test

# Run full integration and concurrency suite with Testcontainers
./mvnw clean verify
```

### Test Suite Highlights
* **`TransferConcurrencyTest`**: Spawns multiple threads executing concurrent transfers between accounts in opposing directions. Verifies that account balances never drift, zero updates are lost, and deterministic primary key sorting guarantees zero deadlocks.
* **`IdempotencyIntegrationTest`**: Fires simultaneous parallel requests with matching idempotency keys to verify that only a single database claim is processed.
* **`LedgerIntegrationTest`**: Tests PostgreSQL triggers to verify that direct SQL queries attempting to alter or delete ledger history are rejected.

---

## CI / CD Pipeline

Every push and pull request to `main` and `master` runs a GitHub Actions workflow:
1. **JDK 21 Setup**: Bootstraps Eclipse Temurin 21 with Maven caching.
2. **Build & Test**: Executes `./mvnw -B clean verify`.
3. **JaCoCo Coverage Enforcement**: Asserts $\ge 80\%$ line coverage across core service domains.
4. **Artifact Upload**: Publishes test and JaCoCo coverage reports.

---

## License

This project is licensed under the [MIT License](LICENSE).
