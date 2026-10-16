package ke.ledgercore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ke.ledgercore.model.AuditEvent;
import ke.ledgercore.model.AuditLog;
import ke.ledgercore.repository.AuditEventRepository;
import ke.ledgercore.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AuditService {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");
    private final AuditEventRepository auditEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditEventRepository, AuditLogRepository auditLogRepository) {
        this.auditEventRepository = auditEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Writes audit entries asynchronously to both PostgreSQL and Elasticsearch via Logstash.
     * Includes correlation ID, actor, IP, and full before/after diff.
     */
    @Async
    public CompletableFuture<AuditLog> recordAuditLog(
            String actorId,
            String action,
            String entityType,
            String entityId,
            Object beforeState,
            Object afterState,
            String ipAddress,
            String userAgent,
            String correlationId) {

        String beforeJson = serialize(beforeState);
        String afterJson = serialize(afterState);

        AuditLog log = AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .beforeState(beforeJson)
                .afterState(afterJson)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();

        AuditLog saved = auditLogRepository.save(log);

        // Emit structured JSON log for Logstash pipeline -> Elasticsearch
        try {
            String json = objectMapper.writeValueAsString(saved);
            AUDIT_LOGGER.info(json);
        } catch (JsonProcessingException e) {
            AUDIT_LOGGER.error("Failed to serialize audit log entry", e);
        }

        return CompletableFuture.completedFuture(saved);
    }

    private String serialize(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    /**
     * Backward-compatible audit event recorder.
     */
    public void audit(String eventType,
                      String action,
                      String outcome,
                      UUID resourceId,
                      String actor,
                      String role,
                      String clientIp,
                      String traceId,
                      String maskedData) {
        AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .description(maskedData)
                .createdAt(Instant.now())
                .build();
        auditEventRepository.save(event);

        recordAuditLog(
                actor,
                action,
                eventType,
                resourceId != null ? resourceId.toString() : null,
                null,
                maskedData,
                clientIp,
                null,
                traceId
        );
    }
}
