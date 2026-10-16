package ke.ledgercore.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_entity", columnList = "entity_id, entity_type"),
        @Index(name = "idx_audit_logs_actor", columnList = "actor_id"),
        @Index(name = "idx_audit_logs_timestamp", columnList = "timestamp")
})
@Data
public class AuditLog {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public AuditLog() {
        this.timestamp = Instant.now();
    }

    public AuditLog(UUID id, String actorId, String action, String entityType, String entityId,
                    String beforeState, String afterState, String ipAddress, String userAgent,
                    String correlationId, Instant timestamp) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static AuditLogBuilder builder() {
        return new AuditLogBuilder();
    }

    public static class AuditLogBuilder {
        private UUID id;
        private String actorId;
        private String action;
        private String entityType;
        private String entityId;
        private String beforeState;
        private String afterState;
        private String ipAddress;
        private String userAgent;
        private String correlationId;
        private Instant timestamp;

        public AuditLogBuilder id(UUID id) { this.id = id; return this; }
        public AuditLogBuilder actorId(String actorId) { this.actorId = actorId; return this; }
        public AuditLogBuilder action(String action) { this.action = action; return this; }
        public AuditLogBuilder entityType(String entityType) { this.entityType = entityType; return this; }
        public AuditLogBuilder entityId(String entityId) { this.entityId = entityId; return this; }
        public AuditLogBuilder beforeState(String beforeState) { this.beforeState = beforeState; return this; }
        public AuditLogBuilder afterState(String afterState) { this.afterState = afterState; return this; }
        public AuditLogBuilder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public AuditLogBuilder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public AuditLogBuilder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public AuditLogBuilder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public AuditLog build() {
            return new AuditLog(id, actorId, action, entityType, entityId, beforeState, afterState, ipAddress, userAgent, correlationId, timestamp);
        }
    }

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
