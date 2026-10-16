package ke.ledgercore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ke.ledgercore.dto.TransactionResponse;
import ke.ledgercore.model.Account;
import ke.ledgercore.model.LedgerEntry;
import ke.ledgercore.model.LedgerTransaction;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.LedgerEntryRepository;
import ke.ledgercore.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Transaction", description = "Transaction details and inquiry")
@RestController
@RequestMapping({"/transactions", "/api/v1/transactions"})
public class TransactionController {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final AccountRepository accountRepository;

    public TransactionController(
            LedgerTransactionRepository transactionRepository,
            LedgerEntryRepository entryRepository,
            AccountRepository accountRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
    }

    @Operation(summary = "Get transaction by ID", description = "Retrieves transaction details and double-entry postings")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','AUDITOR','CUSTOMER')")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable("id") UUID id, Authentication authentication) {
        LedgerTransaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        // If customer, verify ownership of at least one involved account
        boolean isStaff = authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") ||
                a.getAuthority().equals("ROLE_TELLER") ||
                a.getAuthority().equals("ROLE_AUDITOR"));

        if (!isStaff) {
            String username = authentication.getName();
            boolean ownsDebit = false;
            boolean ownsCredit = false;
            if (tx.getDebitAccountId() != null) {
                ownsDebit = accountRepository.findById(tx.getDebitAccountId())
                        .map(a -> a.getUser().getUsername().equals(username))
                        .orElse(false);
            }
            if (tx.getCreditAccountId() != null) {
                ownsCredit = accountRepository.findById(tx.getCreditAccountId())
                        .map(a -> a.getUser().getUsername().equals(username))
                        .orElse(false);
            }
            if (!ownsDebit && !ownsCredit) {
                // Also check entries
                List<LedgerEntry> entries = entryRepository.findByTransactionId(id);
                boolean ownsAnyEntry = entries.stream()
                        .anyMatch(e -> e.getAccount().getUser().getUsername().equals(username));
                if (!ownsAnyEntry) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to transaction");
                }
            }
        }

        List<LedgerEntry> entries = entryRepository.findByTransactionId(id);
        List<TransactionResponse.EntryResponse> entryResponses = entries.stream()
                .map(e -> TransactionResponse.EntryResponse.builder()
                        .id(e.getId())
                        .accountId(e.getAccount().getId())
                        .direction(e.getEntryType())
                        .amount(e.getAmount())
                        .runningBalance(e.getRunningBalance())
                        .currency(e.getCurrency())
                        .build())
                .collect(Collectors.toList());

        TransactionResponse response = TransactionResponse.builder()
                .id(tx.getId())
                .reference(tx.getReference())
                .type(tx.getType())
                .status(tx.getStatus())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .debitAccountId(tx.getDebitAccountId())
                .creditAccountId(tx.getCreditAccountId())
                .idempotencyKey(tx.getIdempotencyKey())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .postedAt(tx.getPostedAt())
                .reversed(tx.isReversed())
                .reversal(tx.isReversal())
                .entries(entryResponses)
                .build();

        return ResponseEntity.ok(response);
    }
}
