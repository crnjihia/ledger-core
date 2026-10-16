# Contributing to Ledger Core

Thank you for your interest in contributing to **Ledger Core**! As a financial engine, financial correctness, double-entry invariance, and concurrency safety are my highest priorities.

---

## Code of Conduct

I am committed to providing a welcoming, inclusive, and professional environment for everyone. Please be respectful and constructive in all discussions and pull requests.

---

## Development Setup

1. **Prerequisites:**
   - Java 21 (Eclipse Temurin recommended)
   - Docker & Docker Compose (Docker Desktop or daemon)
   - Git

2. **Clone and run tests:**
   ```bash
   git clone https://github.com/crnjihia/ledger-core.git
   cd ledger-core
   ./mvnw clean verify
   ```

   *All 19 Testcontainers integration tests will spin up isolated PostgreSQL 15 and Redis 7 containers automatically.*

3. **Running supporting containers:**
   ```bash
   docker compose up -d postgres redis
   ./mvnw spring-boot:run
   ```

---

## Architectural & Banking Guidelines

1. **Double-Entry Principle:** Every transaction must produce balanced credit and debit ledger entries. Never create an asymmetric debit or credit.
2. **Deterministic Locking:** When locking multiple accounts in a single transaction, always sort account IDs ascending before acquiring pessimistic write locks (`findAllByIdInOrderByIdAsc`). This prevents circular deadlocks under concurrent load.
3. **Idempotency Protection:** All mutating endpoints (`/transfers`, `/disbursements`) must enforce idempotency via client-provided keys and SHA-256 payload hash verification.
4. **Immutability:** Never modify or delete existing rows in `ledger_entries`. Ledger entries are append-only. Compensations are executed via explicit reversal entries.

---

## Commit Guidelines

I adhere to the [Conventional Commits](https://www.conventionalcommits.org/) format:

- `feat(scope): ...` for new features
- `fix(scope): ...` for bug fixes
- `docs(scope): ...` for documentation
- `ci(scope): ...` for CI/CD updates
- `test(scope): ...` for unit/integration tests

---

## Submitting Pull Requests

1. Fork the repository and create a feature branch (`git checkout -b feat/my-feature`).
2. Verify all tests pass locally: `./mvnw clean verify`.
3. Commit your changes using conventional commit messages.
4. Push to your branch and open a Pull Request against `main`.
