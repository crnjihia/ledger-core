package ke.ledgercore.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
@Data
public class Transfer {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id", nullable = false)
    private Account toAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "ledger_transaction_id", nullable = false)
    private UUID ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Transfer() {
        this.createdAt = Instant.now();
    }

    public Transfer(UUID id, Account fromAccount, Account toAccount, BigDecimal amount, String description,
                    TransferStatus status, UUID ledgerTransactionId, Instant createdAt) {
        this.id = id;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.description = description;
        this.status = status;
        this.ledgerTransactionId = ledgerTransactionId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static TransferBuilder builder() {
        return new TransferBuilder();
    }

    public static class TransferBuilder {
        private UUID id;
        private Account fromAccount;
        private Account toAccount;
        private BigDecimal amount;
        private String description;
        private TransferStatus status;
        private UUID ledgerTransactionId;
        private Instant createdAt;

        public TransferBuilder id(UUID id) { this.id = id; return this; }
        public TransferBuilder fromAccount(Account fromAccount) { this.fromAccount = fromAccount; return this; }
        public TransferBuilder toAccount(Account toAccount) { this.toAccount = toAccount; return this; }
        public TransferBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public TransferBuilder description(String description) { this.description = description; return this; }
        public TransferBuilder status(TransferStatus status) { this.status = status; return this; }
        public TransferBuilder ledgerTransactionId(UUID ledgerTransactionId) { this.ledgerTransactionId = ledgerTransactionId; return this; }
        public TransferBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Transfer build() {
            return new Transfer(id, fromAccount, toAccount, amount, description, status, ledgerTransactionId, createdAt);
        }
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
