package ke.ledgercore.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ke.ledgercore.dto.PostingEntryDto;
import ke.ledgercore.dto.PostingRequest;
import ke.ledgercore.model.*;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.LedgerEntryRepository;
import ke.ledgercore.repository.LedgerTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;

    private final Counter transferCounter;
    private final Timer transferTimer;
    private final Timer lockTimer;

    public LedgerService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;

        if (meterRegistry != null) {
            this.transferCounter = meterRegistry.counter("ledger.transfers.count");
            this.transferTimer = Timer.builder("ledger.transfers.timer")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry);
            this.lockTimer = Timer.builder("ledger.lock.wait.time")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry);
        } else {
            this.transferCounter = null;
            this.transferTimer = null;
            this.lockTimer = null;
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public UUID post(PostingRequest request) {
        if (transferTimer != null) {
            return transferTimer.record(() -> executePost(request));
        } else {
            return executePost(request);
        }
    }

    private UUID executePost(PostingRequest request) {
        // 1. Validate entries
        List<PostingEntryDto> entries = request.getEntries();
        if (entries == null || entries.size() < 2) {
            throw new IllegalArgumentException("At least two ledger entries required");
        }

        for (PostingEntryDto e : entries) {
            if (e.getAmount() == null || e.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Entry amount must be positive");
            }
            if (e.getEntryType() == null) {
                throw new IllegalArgumentException("Entry type must be specified");
            }
            if (e.getAccountId() == null) {
                throw new IllegalArgumentException("Account ID must be specified");
            }
        }

        BigDecimal totalDebit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(PostingEntryDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(PostingEntryDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("Total debits (" + totalDebit + ") must equal total credits (" + totalCredit + ")");
        }

        // 2. Lock accounts in ascending ID order (SELECT ... FOR UPDATE) to eliminate deadlocks
        List<UUID> accountIds = entries.stream()
                .map(PostingEntryDto::getAccountId)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        long lockStart = System.nanoTime();
        List<Account> accounts = accountRepository.findAllByIdInOrderByIdAsc(accountIds);
        if (lockTimer != null) {
            lockTimer.record(System.nanoTime() - lockStart, java.util.concurrent.TimeUnit.NANOSECONDS);
        }

        Map<UUID, Account> accountMap = new HashMap<>();
        for (Account acc : accounts) {
            accountMap.put(acc.getId(), acc);
        }

        if (accountMap.size() != accountIds.size()) {
            throw new IllegalArgumentException("One or more accounts not found");
        }

        // 3. Status checks and balance verification
        for (PostingEntryDto e : entries) {
            Account acc = accountMap.get(e.getAccountId());
            if (acc.getStatus() != AccountStatus.ACTIVE) {
                throw new IllegalArgumentException("Account " + acc.getId() + " is " + acc.getStatus() + ", expected ACTIVE");
            }
            if (e.getEntryType() == EntryType.DEBIT) {
                // Suspense accounts can act as clearing accounts, but all regular accounts require sufficient funds
                if (acc.getType() != ke.ledgercore.model.AccountType.SUSPENSE) {
                    if (acc.getBalance().compareTo(e.getAmount()) < 0) {
                        throw new org.springframework.dao.DataIntegrityViolationException("Insufficient funds on account " + acc.getId());
                    }
                }
            }
        }

        // 4. Create Transaction entity
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String reference = "TXN-" + dateStr + "-" + suffix;

        UUID debitAccountId = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(PostingEntryDto::getAccountId)
                .findFirst().orElse(null);

        UUID creditAccountId = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(PostingEntryDto::getAccountId)
                .findFirst().orElse(null);

        LedgerTransaction tx = LedgerTransaction.builder()
                .transactionNumber(reference)
                .reference(reference)
                .type(request.getType() != null ? request.getType() : TransactionType.TRANSFER)
                .status(TransactionStatus.POSTED)
                .reversal(request.getType() == TransactionType.REVERSAL)
                .amount(totalCredit)
                .currency("KES")
                .debitAccountId(debitAccountId)
                .creditAccountId(creditAccountId)
                .idempotencyKey(request.getIdempotencyKey())
                .description(request.getDescription())
                .postedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        LedgerTransaction savedTx = ledgerTransactionRepository.save(tx);

        // 5. Apply ledger entries, update account balances, and compute running balances
        for (PostingEntryDto e : entries) {
            Account acc = accountMap.get(e.getAccountId());
            BigDecimal newBalance;
            if (e.getEntryType() == EntryType.DEBIT) {
                newBalance = acc.getBalance().subtract(e.getAmount());
            } else {
                newBalance = acc.getBalance().add(e.getAmount());
            }
            acc.setBalance(newBalance);

            LedgerEntry le = LedgerEntry.builder()
                    .transaction(savedTx)
                    .account(acc)
                    .amount(e.getAmount())
                    .entryType(e.getEntryType())
                    .runningBalance(newBalance)
                    .currency(acc.getCurrency() != null ? acc.getCurrency() : "KES")
                    .createdAt(Instant.now())
                    .build();

            ledgerEntryRepository.save(le);
        }

        // Batch save updated accounts
        accountRepository.saveAll(accounts);

        if (transferCounter != null) {
            transferCounter.increment();
        }

        return savedTx.getId();
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public UUID reverse(UUID originalTxId, String reason) {
        LedgerTransaction original = ledgerTransactionRepository.findById(originalTxId)
                .orElseThrow(() -> new IllegalArgumentException("Original transaction not found"));

        if (original.isReversed() || original.getStatus() == TransactionStatus.REVERSED) {
            throw new IllegalArgumentException("Transaction already reversed");
        }
        if (original.isReversal() || original.getType() == TransactionType.REVERSAL) {
            throw new IllegalArgumentException("Cannot reverse a reversal transaction");
        }

        List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(originalTxId);
        if (originalEntries.isEmpty()) {
            throw new IllegalStateException("Original transaction has no ledger entries");
        }

        List<PostingEntryDto> reversalEntries = originalEntries.stream()
                .map(le -> {
                    PostingEntryDto rev = new PostingEntryDto();
                    rev.setAccountId(le.getAccount().getId());
                    rev.setAmount(le.getAmount());
                    rev.setEntryType(le.getEntryType() == EntryType.DEBIT ? EntryType.CREDIT : EntryType.DEBIT);
                    return rev;
                })
                .collect(Collectors.toList());

        PostingRequest revRequest = PostingRequest.builder()
                .entries(reversalEntries)
                .type(TransactionType.REVERSAL)
                .description("Reversal of " + original.getReference() + (reason != null ? ": " + reason : ""))
                .build();

        UUID reversalTxId = this.post(revRequest);

        original.setReversed(true);
        original.setStatus(TransactionStatus.REVERSED);
        ledgerTransactionRepository.save(original);

        return reversalTxId;
    }
}
