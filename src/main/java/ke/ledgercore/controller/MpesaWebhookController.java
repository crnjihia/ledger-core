package ke.ledgercore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ke.ledgercore.dto.DisbursementCallbackRequest;
import ke.ledgercore.service.DisbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Webhooks", description = "External service callbacks")
@RestController
@RequestMapping({"/webhooks", "/api/v1/webhooks"})
public class MpesaWebhookController {

    private final DisbursementService disbursementService;

    public MpesaWebhookController(DisbursementService disbursementService) {
        this.disbursementService = disbursementService;
    }

    @Operation(summary = "M-Pesa B2C callback result", description = "Receives asynchronous settlement results from M-Pesa")
    @PostMapping("/mpesa/result")
    public ResponseEntity<Void> mpesaResultCallback(@RequestBody DisbursementCallbackRequest request) {
        disbursementService.handleCallback(request);
        return ResponseEntity.ok().build();
    }
}
