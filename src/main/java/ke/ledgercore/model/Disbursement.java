package ke.ledgercore.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disbursements")
@Data
public class Disbursement {
    @Id
    @GeneratedValue
    private UUID id;

    // Source account (customer account) from which money is debited
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    // Settlement account where money is credited (pre-configured system account)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account settlementAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "msisdn", nullable = false, length = 20)
    private String msisdn;

    @Column(length = 255)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisbursementStatus status;

    @Column(length = 50)
    private String receipt;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Disbursement() {
        this.createdAt = Instant.now();
    }

    public Disbursement(UUID id, Account fromAccount, Account settlementAccount, BigDecimal amount,
                        String msisdn, String remarks, DisbursementStatus status, String receipt,
                        UUID ledgerTransactionId, Instant createdAt) {
        this.id = id;
        this.fromAccount = fromAccount;
        this.settlementAccount = settlementAccount;
        this.amount = amount;
        this.msisdn = msisdn;
        this.remarks = remarks;
        this.status = status;
        this.receipt = receipt;
        this.ledgerTransactionId = ledgerTransactionId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static DisbursementBuilder builder() {
        return new DisbursementBuilder();
    }

    public static class DisbursementBuilder {
        private UUID id;
        private Account fromAccount;
        private Account settlementAccount;
        private BigDecimal amount;
        private String msisdn;
        private String remarks;
        private DisbursementStatus status;
        private String receipt;
        private UUID ledgerTransactionId;
        private Instant createdAt;

        public DisbursementBuilder id(UUID id) { this.id = id; return this; }
        public DisbursementBuilder fromAccount(Account fromAccount) { this.fromAccount = fromAccount; return this; }
        public DisbursementBuilder settlementAccount(Account settlementAccount) { this.settlementAccount = settlementAccount; return this; }
        public DisbursementBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public DisbursementBuilder msisdn(String msisdn) { this.msisdn = msisdn; return this; }
        public DisbursementBuilder remarks(String remarks) { this.remarks = remarks; return this; }
        public DisbursementBuilder status(DisbursementStatus status) { this.status = status; return this; }
        public DisbursementBuilder receipt(String receipt) { this.receipt = receipt; return this; }
        public DisbursementBuilder ledgerTransactionId(UUID ledgerTransactionId) { this.ledgerTransactionId = ledgerTransactionId; return this; }
        public DisbursementBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Disbursement build() {
            return new Disbursement(id, fromAccount, settlementAccount, amount, msisdn, remarks, status, receipt, ledgerTransactionId, createdAt);
        }
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
