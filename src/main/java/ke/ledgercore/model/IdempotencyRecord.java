package ke.ledgercore.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
@Data
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "completed", nullable = false)
    private Boolean completed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public IdempotencyRecord() {
        this.completed = false;
        this.createdAt = Instant.now();
    }

    public IdempotencyRecord(String idempotencyKey, String requestHash, String responseBody,
                             Integer statusCode, Boolean completed, Instant createdAt, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.statusCode = statusCode;
        this.completed = completed != null ? completed : false;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = expiresAt;
    }

    public static IdempotencyRecordBuilder builder() {
        return new IdempotencyRecordBuilder();
    }

    public static class IdempotencyRecordBuilder {
        private String idempotencyKey;
        private String requestHash;
        private String responseBody;
        private Integer statusCode;
        private Boolean completed = false;
        private Instant createdAt;
        private Instant expiresAt;

        public IdempotencyRecordBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public IdempotencyRecordBuilder key(String key) { this.idempotencyKey = key; return this; }
        public IdempotencyRecordBuilder requestHash(String requestHash) { this.requestHash = requestHash; return this; }
        public IdempotencyRecordBuilder responseBody(String responseBody) { this.responseBody = responseBody; return this; }
        public IdempotencyRecordBuilder statusCode(Integer statusCode) { this.statusCode = statusCode; return this; }
        public IdempotencyRecordBuilder completed(Boolean completed) { this.completed = completed; return this; }
        public IdempotencyRecordBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public IdempotencyRecordBuilder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }

        public IdempotencyRecord build() {
            return new IdempotencyRecord(idempotencyKey, requestHash, responseBody, statusCode, completed, createdAt, expiresAt);
        }
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (completed == null) {
            completed = false;
        }
    }

    public String getKey() {
        return idempotencyKey;
    }

    public void setKey(String key) {
        this.idempotencyKey = key;
    }
}
