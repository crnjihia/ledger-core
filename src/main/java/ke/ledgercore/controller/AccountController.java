package ke.ledgercore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ke.ledgercore.audit.Auditable;
import ke.ledgercore.dto.AccountCreateRequest;
import ke.ledgercore.dto.AccountResponse;
import ke.ledgercore.dto.AccountStatusUpdateRequest;
import ke.ledgercore.dto.StatementEntryDTO;
import ke.ledgercore.dto.StatementResponse;
import ke.ledgercore.model.Account;
import ke.ledgercore.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Account", description = "Account management endpoints")
@RestController
@RequestMapping({"/accounts", "/api/v1/accounts"})
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Create a new account", description = "Creates an account for the specified user/customer ID")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Auditable(action = "CREATE_ACCOUNT", entityType = "Account")
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountCreateRequest request) {
        var account = accountService.createAccount(request);
        return new ResponseEntity<>(AccountResponse.from(account), HttpStatus.CREATED);
    }

    @Operation(summary = "Get account by ID", description = "Retrieves account details")
    @GetMapping("/{id}")
    @PreAuthorize("@accountSecurity.isOwner(#p0, authentication.name) or hasAnyRole('ADMIN','TELLER','AUDITOR')")
    public ResponseEntity<AccountResponse> getAccount(@org.springframework.security.core.parameters.P("id") @PathVariable("id") UUID id) {
        return accountService.getAccount(id)
                .map(AccountResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Operation(summary = "Get account balance", description = "Retrieves the current balance of the account")
    @GetMapping("/{id}/balance")
    @PreAuthorize("@accountSecurity.isOwner(#p0, authentication.name) or hasAnyRole('ADMIN','TELLER','AUDITOR')")
    public ResponseEntity<Map<String, Object>> getBalance(@org.springframework.security.core.parameters.P("id") @PathVariable("id") UUID id) {
        Account account = accountService.getAccount(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        Map<String, Object> resp = new HashMap<>();
        resp.put("accountId", account.getId());
        resp.put("accountNumber", account.getAccountNumber());
        resp.put("balance", account.getBalance());
        resp.put("currency", account.getCurrency());
        resp.put("status", account.getStatus());
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "List accounts", description = "List accounts belonging to caller or all accounts for staff")
    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts(Authentication authentication) {
        List<AccountResponse> responses = accountService.listAccounts(authentication)
                .stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Update account status", description = "Freezes or closes an account (ADMIN only)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "UPDATE_ACCOUNT_STATUS", entityType = "Account")
    public ResponseEntity<Void> updateStatus(@PathVariable("id") UUID id, @RequestBody AccountStatusUpdateRequest request) {
        accountService.updateStatus(id, request.getStatus());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get account statement", description = "Paginated list of ledger entries with running balance")
    @GetMapping("/{id}/statement")
    @PreAuthorize("@accountSecurity.isOwner(#p0, authentication.name) or hasAnyRole('ADMIN','TELLER','AUDITOR')")
    public ResponseEntity<StatementResponse> getStatement(
            @org.springframework.security.core.parameters.P("id") @PathVariable("id") UUID id,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Page<StatementEntryDTO> pageResult = accountService.getStatement(id, from, to, page, size);
        StatementResponse resp = new StatementResponse(
                pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );
        return ResponseEntity.ok(resp);
    }
}
