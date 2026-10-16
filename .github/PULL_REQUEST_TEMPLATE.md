## Description
<!-- Provide a brief summary of the changes and the financial context. -->

## Type of Change
- [ ] 🐛 Bug fix (non-breaking change fixing an issue)
- [ ] ✨ New feature (non-breaking change adding functionality)
- [ ] 💥 Breaking change (fix or feature causing existing APIs to change)
- [ ] 🔒 Security / Concurrency fix
- [ ] 📚 Documentation update

## Financial Invariant & Concurrency Verification
- [ ] Mathematical zero-sum invariant preserved ($\sum \text{Credits} - \sum \text{Debits} = 0$)
- [ ] Row-level locking order adheres to deterministic ascending ID rule
- [ ] Idempotency key handling validated (Redis fast-path + DB claim + hash check)
- [ ] Testcontainers integration and concurrency tests pass locally (`./mvnw clean verify`)

## Related Issues
<!-- Link related issues e.g. Closes #123 -->
