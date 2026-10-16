package ke.ledgercore.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
@Data
public class AuditEvent {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditEvent() {
        this.createdAt = Instant.now();
    }

    public AuditEvent(UUID id, String eventType, String description, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.description = description;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static AuditEventBuilder builder() {
        return new AuditEventBuilder();
    }

    public static class AuditEventBuilder {
        private UUID id;
        private String eventType;
        private String description;
        private Instant createdAt;

        public AuditEventBuilder id(UUID id) { this.id = id; return this; }
        public AuditEventBuilder eventType(String eventType) { this.eventType = eventType; return this; }
        public AuditEventBuilder description(String description) { this.description = description; return this; }
        public AuditEventBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public AuditEvent build() {
            return new AuditEvent(id, eventType, description, createdAt);
        }
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
