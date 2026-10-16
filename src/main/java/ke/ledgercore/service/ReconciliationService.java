package ke.ledgercore.service;

import ke.ledgercore.model.Account;
import ke.ledgercore.model.EntryType;
import ke.ledgercore.model.LedgerEntry;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;

    public ReconciliationService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AuditService auditService
    ) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
    }

    /**
     * Nightly reconciliation job verifying SUM(credits) - SUM(debits) == 0 across the ledger.
     */
    @Scheduled(cron = "${reconciliation.cron:0 0 0 * * *}")
    public void runNightlyReconciliation() {
        log.info("Starting scheduled nightly ledger reconciliation job...");
        Map<UUID, String> discrepancies = reconcile();
        if (discrepancies.isEmpty()) {
            log.info("Nightly reconciliation completed successfully: 0 discrepancies found. Zero-sum invariant holds.");
            auditService.audit("RECONCILIATION", "NIGHTLY_RECONCILIATION", "SUCCESS", null, "system", "SYSTEM", null, null, "Zero discrepancies, zero-sum invariant holds");
        } else {
            log.error("Nightly reconciliation detected discrepancies: {}", discrepancies);
            auditService.audit("RECONCILIATION", "NIGHTLY_RECONCILIATION", "FAILURE", null, "system", "SYSTEM", null, null, "Discrepancies found: " + discrepancies.size());
        }
    }

    public Map<UUID, String> reconcile() {
        Map<UUID, String> result = new HashMap<>();
        List<LedgerEntry> entries = ledgerEntryRepository.findAll();

        // Core Requirement: verifies SUM(credits) - SUM(debits) == 0 across the ledger
        BigDecimal globalCredits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal globalDebits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal globalImbalance = globalCredits.subtract(globalDebits);
        if (globalImbalance.compareTo(BigDecimal.ZERO) != 0) {
            result.put(UUID.randomUUID(), "Global imbalance across ledger: credits=" + globalCredits + ", debits=" + globalDebits + ", imbalance=" + globalImbalance);
        }

        return result;
    }
}
