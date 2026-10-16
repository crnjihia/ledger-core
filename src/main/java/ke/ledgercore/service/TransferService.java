package ke.ledgercore.service;

import ke.ledgercore.audit.Auditable;
import ke.ledgercore.dto.PostingEntryDto;
import ke.ledgercore.dto.PostingRequest;
import ke.ledgercore.dto.TransferCreateRequest;
import ke.ledgercore.dto.TransferResponse;
import ke.ledgercore.model.*;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.AuditEventRepository;
import ke.ledgercore.repository.TransferRepository;
import ke.ledgercore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;

    public TransferService(
            AccountRepository accountRepository,
            LedgerService ledgerService,
            TransferRepository transferRepository,
            UserRepository userRepository,
            AuditEventRepository auditEventRepository
    ) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Value("${transfer.fee:0}")
    private BigDecimal transferFee = BigDecimal.ZERO;

    @Value("${transfer.limit:1000000}")
    private BigDecimal transferLimit = new BigDecimal("1000000");

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("500000");

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Auditable(action = "CREATE_TRANSFER", entityType = "Transfer")
    public TransferResponse createTransfer(TransferCreateRequest request, Authentication auth) {
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getAmount().compareTo(transferLimit) > 0) {
            throw new IllegalArgumentException("Amount exceeds per-transaction limit");
        }

        List<UUID> accountIds = java.util.stream.Stream.of(request.getFromAccountId(), request.getToAccountId())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<Account> lockedAccounts = accountRepository.findAllByIdInOrderByIdAsc(accountIds);
        Map<UUID, Account> accountMap = lockedAccounts.stream().collect(Collectors.toMap(Account::getId, a -> a));
        Account fromAccount = accountMap.get(request.getFromAccountId());
        Account toAccount = accountMap.get(request.getToAccountId());

        if (fromAccount == null || toAccount == null) {
            throw new IllegalArgumentException("One or more accounts not found");
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE || toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Both accounts must be ACTIVE");
        }

        // Role check: CUSTOMER can only transfer FROM own accounts
        if (auth != null) {
            String username = auth.getName();
            boolean isCustomer = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
            if (isCustomer) {
                boolean owns = fromAccount.getUser().getUsername().equals(username);
                if (!owns) {
                    throw new DataIntegrityViolationException("Customers can only transfer from their own accounts");
                }
            }
        }

        BigDecimal fee = (transferFee != null && transferFee.compareTo(BigDecimal.ZERO) > 0) ? transferFee : BigDecimal.ZERO;
        BigDecimal totalDebit = request.getAmount().add(fee);

        List<PostingEntryDto> entries = new ArrayList<>();

        // Debit source
        PostingEntryDto debit = new PostingEntryDto();
        debit.setAccountId(fromAccount.getId());
        debit.setAmount(totalDebit);
        debit.setEntryType(EntryType.DEBIT);
        entries.add(debit);

        // Credit destination
        PostingEntryDto creditDest = new PostingEntryDto();
        creditDest.setAccountId(toAccount.getId());
        creditDest.setAmount(request.getAmount());
        creditDest.setEntryType(EntryType.CREDIT);
        entries.add(creditDest);

        // Credit fee account if fee > 0
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            Account feeAccount = accountRepository.findAll().stream()
                    .filter(a -> "FEE".equals(a.getAccountNumber()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("FEE account not found"));

            PostingEntryDto creditFee = new PostingEntryDto();
            creditFee.setAccountId(feeAccount.getId());
            creditFee.setAmount(fee);
            creditFee.setEntryType(EntryType.CREDIT);
            entries.add(creditFee);
        }

        PostingRequest postingRequest = PostingRequest.builder()
                .entries(entries)
                .type(TransactionType.TRANSFER)
                .description(request.getDescription())
                .build();

        UUID ledgerTxId = ledgerService.post(postingRequest);

        Transfer transfer = Transfer.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(TransferStatus.COMPLETED)
                .ledgerTransactionId(ledgerTxId)
                .build();
        Transfer saved = transferRepository.save(transfer);

        if (request.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            AuditEvent audit = AuditEvent.builder()
                    .eventType("TRANSFER_HIGH_VALUE")
                    .description("High-value transfer of " + request.getAmount() + " from " + fromAccount.getAccountNumber() + " to " + toAccount.getAccountNumber())
                    .createdAt(Instant.now())
                    .build();
            auditEventRepository.save(audit);
        }

        return TransferResponse.builder()
                .id(saved.getId())
                .fromAccountId(saved.getFromAccount().getId())
                .toAccountId(saved.getToAccount().getId())
                .amount(saved.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "KES")
                .description(saved.getDescription())
                .status(saved.getStatus().name())
                .build();
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID id) {
        Transfer t = transferRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        return TransferResponse.builder()
                .id(t.getId())
                .fromAccountId(t.getFromAccount().getId())
                .toAccountId(t.getToAccount().getId())
                .amount(t.getAmount())
                .currency("KES")
                .description(t.getDescription())
                .status(t.getStatus().name())
                .build();
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Auditable(action = "REVERSE_TRANSFER", entityType = "Transfer")
    public UUID reverseTransfer(UUID transferId) {
        Transfer t = transferRepository.findById(transferId).orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (t.getStatus() == TransferStatus.REVERSED) {
            throw new IllegalArgumentException("Transfer already reversed");
        }
        UUID reversalTxId = ledgerService.reverse(t.getLedgerTransactionId(), "Transfer reversal");
        t.setStatus(TransferStatus.REVERSED);
        transferRepository.save(t);
        return reversalTxId;
    }
}
