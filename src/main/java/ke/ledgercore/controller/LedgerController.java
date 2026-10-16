package ke.ledgercore.controller;

import ke.ledgercore.dto.PostingRequest;
import ke.ledgercore.dto.StatementResponse;
import ke.ledgercore.service.IdempotencyService;
import ke.ledgercore.service.LedgerService;
import ke.ledgercore.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

@Tag(name = "Ledger", description = "Ledger posting and reversal endpoints")
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final ReconciliationService reconciliationService;
    private final IdempotencyService idempotencyService;

    public LedgerController(LedgerService ledgerService, ReconciliationService reconciliationService, IdempotencyService idempotencyService) {
        this.ledgerService = ledgerService;
        this.reconciliationService = reconciliationService;
        this.idempotencyService = idempotencyService;
    }

    @Operation(summary = "Post a ledger transaction", description = "Creates a double-entry posting (debit/credit)")
    @PostMapping("/post")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<UUID> post(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                   @RequestBody PostingRequest request) {
        return idempotencyService.handle(idempotencyKey, request, () -> {
            UUID txId = ledgerService.post(request);
            return ResponseEntity.ok(txId);
        });
    }

    @Operation(summary = "Reverse a ledger transaction", description = "Creates a reversal entry for a given transaction")
    @PostMapping("/reverse/{transactionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UUID> reverse(@PathVariable("transactionId") UUID transactionId,
                                          @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                          @RequestParam(value = "reason", required = false) String reason) {
        return idempotencyService.handle(idempotencyKey, transactionId, () -> {
            UUID reversalId = ledgerService.reverse(transactionId, reason);
            return ResponseEntity.ok(reversalId);
        });
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> reconciliation() {
        return ResponseEntity.ok(reconciliationService.reconcile());
    }
}
