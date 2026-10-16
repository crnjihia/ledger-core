package ke.ledgercore.repository;

import ke.ledgercore.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);
    Page<LedgerEntry> findByAccountIdAndCreatedAtBetween(UUID accountId, Instant from, Instant to, Pageable pageable);
    Page<LedgerEntry> findByAccountIdAndCreatedAtAfter(UUID accountId, Instant from, Pageable pageable);
    Page<LedgerEntry> findByAccountIdAndCreatedAtBefore(UUID accountId, Instant to, Pageable pageable);
    java.util.List<LedgerEntry> findByTransactionId(UUID transactionId);
}

