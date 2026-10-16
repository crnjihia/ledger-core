package ke.ledgercore.service;

import ke.ledgercore.dto.AccountCreateRequest;
import ke.ledgercore.dto.StatementEntryDTO;
import ke.ledgercore.model.Account;
import ke.ledgercore.model.AccountStatus;
import ke.ledgercore.model.LedgerEntry;
import ke.ledgercore.model.LedgerTransaction;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.UserRepository;
import ke.ledgercore.repository.LedgerEntryRepository;
import ke.ledgercore.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final SecureRandom random;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            LedgerEntryRepository ledgerEntryRepository,
            LedgerTransactionRepository ledgerTransactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.random = new SecureRandom();
    }

    @Transactional
    public Account createAccount(AccountCreateRequest request) {
        UUID effectiveId = request.getEffectiveCustomerId();
        if (effectiveId == null) {
            throw new IllegalArgumentException("User/Customer ID must not be null");
        }
        var user = userRepository.findById(effectiveId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String accountNumber = generateUniqueAccountNumber();
        Account account = Account.builder()
                .user(user)
                .customerId(effectiveId)
                .accountNumber(accountNumber)
                .type(request.getType() != null ? request.getType() : ke.ledgercore.model.AccountType.SAVINGS)
                .currency(request.getCurrency() != null ? request.getCurrency() : "KES")
                .balance(BigDecimal.ZERO.setScale(4))
                .status(AccountStatus.ACTIVE)
                .build();
        return accountRepository.save(account);
    }

    private String generateUniqueAccountNumber() {
        while (true) {
            int nine = 100_000_000 + random.nextInt(900_000_000);
            String base = String.valueOf(nine);
            int sum = base.chars().map(c -> c - '0').sum();
            int check = sum % 10;
            String candidate = base + check;
            if (accountRepository.findByAccountNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
    }

    public Optional<Account> getAccount(UUID id) {
        return accountRepository.findById(id);
    }

    public List<Account> listAccounts(Authentication authentication) {
        boolean isAdminOrTeller = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_TELLER"));
        if (isAdminOrTeller) {
            return accountRepository.findAll();
        } else {
            String username = authentication.getName();
            return accountRepository.findAll().stream()
                    .filter(acc -> acc.getUser().getUsername().equals(username))
                    .toList();
        }
    }

    @Transactional
    public void updateStatus(UUID accountId, AccountStatus newStatus) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (newStatus == AccountStatus.CLOSED && account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new DataIntegrityViolationException("Cannot close account with non-zero balance");
        }
        account.setStatus(newStatus);
        accountRepository.save(account);
    }

    public Page<StatementEntryDTO> getStatement(UUID accountId, Instant from, Instant to, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt"));
        Page<LedgerEntry> entries;
        if (from != null && to != null) {
            entries = ledgerEntryRepository.findByAccountIdAndCreatedAtBetween(accountId, from, to, pageable);
        } else if (from != null) {
            entries = ledgerEntryRepository.findByAccountIdAndCreatedAtAfter(accountId, from, pageable);
        } else if (to != null) {
            entries = ledgerEntryRepository.findByAccountIdAndCreatedAtBefore(accountId, to, pageable);
        } else {
            entries = ledgerEntryRepository.findByAccountId(accountId, pageable);
        }
        BigDecimal running = BigDecimal.ZERO;
        List<StatementEntryDTO> dtoList = new ArrayList<>();
        for (LedgerEntry entry : entries) {
            running = running.add(entry.getAmount());
            UUID txId = entry.getTransaction() != null ? entry.getTransaction().getId() : null;
            dtoList.add(new StatementEntryDTO(entry.getId(), txId, entry.getAmount(), entry.getCreatedAt(), running));
        }
        return new PageImpl<>(dtoList, pageable, entries.getTotalElements());
    }
}
