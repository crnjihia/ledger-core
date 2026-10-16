package ke.ledgercore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ke.ledgercore.dto.DisbursementCallbackRequest;
import ke.ledgercore.dto.DisbursementCreateRequest;
import ke.ledgercore.dto.DisbursementResponse;
import ke.ledgercore.service.DisbursementService;
import ke.ledgercore.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Disbursement", description = "M-Pesa B2C disbursement endpoints")
@RestController
@RequestMapping({"/disbursements", "/api/v1/disbursements"})
public class DisbursementController {

    private final DisbursementService disbursementService;
    private final IdempotencyService idempotencyService;

    public DisbursementController(DisbursementService disbursementService, IdempotencyService idempotencyService) {
        this.disbursementService = disbursementService;
        this.idempotencyService = idempotencyService;
    }

    @Value("${disbursement.callback-secret:REPLACE_ME}")
    private String callbackSecret;

    @Operation(summary = "Create a disbursement", description = "Initiates an M-Pesa B2C mobile money disbursement")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ResponseEntity<DisbursementResponse> createDisbursement(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody DisbursementCreateRequest request,
            Authentication authentication) {
        return idempotencyService.handle(idempotencyKey, request, () -> {
            DisbursementResponse resp = disbursementService.createDisbursement(request, authentication);
            return ResponseEntity.ok(resp);
        });
    }

    @Operation(summary = "Get disbursement by ID", description = "Retrieves status and receipt of disbursement")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ResponseEntity<DisbursementResponse> getDisbursement(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(disbursementService.getDisbursement(id));
    }

    @Operation(summary = "Reverse a disbursement", description = "Reverses a disbursement transaction (ADMIN only)")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UUID> reverseDisbursement(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return idempotencyService.handle(idempotencyKey, id, () -> {
            UUID revId = disbursementService.reverseDisbursement(id);
            return ResponseEntity.ok(revId);
        });
    }

    @Operation(summary = "M-Pesa callback webhook", description = "Callback endpoint for M-Pesa B2C result notification")
    @PostMapping({"/mpesa/callback", "/webhooks/mpesa/result"})
    public ResponseEntity<Void> callback(
            @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
            @RequestBody DisbursementCallbackRequest request) {
        if (!"REPLACE_ME".equals(callbackSecret) && secret != null && !callbackSecret.equals(secret)) {
            return ResponseEntity.status(403).build();
        }
        disbursementService.handleCallback(request);
        return ResponseEntity.ok().build();
    }
}
