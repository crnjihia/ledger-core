package ke.ledgercore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ke.ledgercore.dto.TransferCreateRequest;
import ke.ledgercore.dto.TransferResponse;
import ke.ledgercore.service.IdempotencyService;
import ke.ledgercore.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Transfer", description = "Internal money transfers")
@RestController
@RequestMapping({"/transfers", "/api/v1/transfers"})
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;

    public TransferController(TransferService transferService, IdempotencyService idempotencyService) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
    }

    @Operation(summary = "Create a money transfer", description = "Transfers funds between two accounts (requires Idempotency-Key header)")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody TransferCreateRequest request,
            Authentication authentication) {
        return idempotencyService.handle(idempotencyKey, request, () -> {
            TransferResponse resp = transferService.createTransfer(request, authentication);
            return ResponseEntity.ok(resp);
        });
    }

    @Operation(summary = "Get transfer by ID", description = "Retrieves transfer details")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable("id") UUID id) {
        TransferResponse resp = transferService.getTransfer(id);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Reverse a transfer", description = "Reverses an internal transfer (ADMIN only)")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UUID> reverseTransfer(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return idempotencyService.handle(idempotencyKey, id, () -> {
            UUID revId = transferService.reverseTransfer(id);
            return ResponseEntity.ok(revId);
        });
    }
}
