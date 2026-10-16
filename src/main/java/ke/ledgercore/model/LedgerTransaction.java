package ke.ledgercore.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions", uniqueConstraints = {
        @UniqueConstraint(columnNames = "transaction_number"),
        @UniqueConstraint(columnNames = "reference")
})
@Data
public class LedgerTransaction {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "transaction_number", length = 100)
    private String transactionNumber;

    @Column(name = "reference", length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "transaction_status")
    @org.hibernate.annotations.JdbcType(org.hibernate.dialect.PostgreSQLEnumJdbcType.class)
    private TransactionStatus status;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "debit_account_id")
    private UUID debitAccountId;

    @Column(name = "credit_account_id")
    private UUID creditAccountId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean reversed;

    @Column(nullable = false)
    private boolean reversal;

    @Version
    @Column(nullable = false)
    private Long version;

    public LedgerTransaction() {
        this.type = TransactionType.TRANSFER;
        this.status = TransactionStatus.PENDING;
        this.amount = BigDecimal.ZERO;
        this.currency = "KES";
        this.createdAt = Instant.now();
        this.reversed = false;
        this.reversal = false;
    }

    public LedgerTransaction(UUID id, String transactionNumber, String reference, TransactionType type,
                             TransactionStatus status, BigDecimal amount, String currency, UUID debitAccountId,
                             UUID creditAccountId, String idempotencyKey, String description, Instant postedAt,
                             Instant createdAt, boolean reversed, boolean reversal, Long version) {
        this.id = id;
        this.transactionNumber = transactionNumber;
        this.reference = reference != null ? reference : transactionNumber;
        this.type = type != null ? type : TransactionType.TRANSFER;
        this.status = status != null ? status : TransactionStatus.PENDING;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "KES";
        this.debitAccountId = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.postedAt = postedAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.reversed = reversed;
        this.reversal = reversal;
        this.version = version;
    }

    public static LedgerTransactionBuilder builder() {
        return new LedgerTransactionBuilder();
    }

    public static class LedgerTransactionBuilder {
        private UUID id;
        private String transactionNumber;
        private String reference;
        private TransactionType type = TransactionType.TRANSFER;
        private TransactionStatus status = TransactionStatus.PENDING;
        private BigDecimal amount = BigDecimal.ZERO;
        private String currency = "KES";
        private UUID debitAccountId;
        private UUID creditAccountId;
        private String idempotencyKey;
        private String description;
        private Instant postedAt;
        private Instant createdAt;
        private boolean reversed;
        private boolean reversal;
        private Long version;

        public LedgerTransactionBuilder id(UUID id) { this.id = id; return this; }
        public LedgerTransactionBuilder transactionNumber(String transactionNumber) { this.transactionNumber = transactionNumber; return this; }
        public LedgerTransactionBuilder reference(String reference) { this.reference = reference; return this; }
        public LedgerTransactionBuilder type(TransactionType type) { this.type = type; return this; }
        public LedgerTransactionBuilder status(TransactionStatus status) { this.status = status; return this; }
        public LedgerTransactionBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public LedgerTransactionBuilder currency(String currency) { this.currency = currency; return this; }
        public LedgerTransactionBuilder debitAccountId(UUID debitAccountId) { this.debitAccountId = debitAccountId; return this; }
        public LedgerTransactionBuilder creditAccountId(UUID creditAccountId) { this.creditAccountId = creditAccountId; return this; }
        public LedgerTransactionBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public LedgerTransactionBuilder description(String description) { this.description = description; return this; }
        public LedgerTransactionBuilder postedAt(Instant postedAt) { this.postedAt = postedAt; return this; }
        public LedgerTransactionBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public LedgerTransactionBuilder reversed(boolean reversed) { this.reversed = reversed; return this; }
        public LedgerTransactionBuilder reversal(boolean reversal) { this.reversal = reversal; return this; }
        public LedgerTransactionBuilder version(Long version) { this.version = version; return this; }

        public LedgerTransaction build() {
            return new LedgerTransaction(id, transactionNumber, reference, type, status, amount, currency,
                    debitAccountId, creditAccountId, idempotencyKey, description, postedAt, createdAt,
                    reversed, reversal, version);
        }
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (type == null) {
            type = TransactionType.TRANSFER;
        }
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (currency == null) {
            currency = "KES";
        }
    }

    public String getReference() {
        return reference != null ? reference : transactionNumber;
    }

    public void setReference(String ref) {
        this.reference = ref;
        if (this.transactionNumber == null) {
            this.transactionNumber = ref;
        }
    }
}
