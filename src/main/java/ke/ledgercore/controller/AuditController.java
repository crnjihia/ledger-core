package ke.ledgercore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ke.ledgercore.model.AuditLog;
import ke.ledgercore.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "Audit", description = "Audit log retrieval")
@RestController
@RequestMapping({"/audit-logs", "/api/v1/audit-logs", "/api/v1/audit"})
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Operation(summary = "Retrieve audit logs", description = "Paginated filterable list of audit logs for ADMIN or AUDITOR role")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(name = "entityId", required = false) String entityId,
            @RequestParam(name = "actorId", required = false) String actorId,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        String effectiveActorId = actorId != null ? actorId : actor;
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> result = auditLogRepository.findFiltered(entityId, effectiveActorId, from, to, pageable);
        return ResponseEntity.ok(result);
    }
}
