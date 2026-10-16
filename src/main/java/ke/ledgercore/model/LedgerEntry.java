package ke.ledgercore.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Data
public class LedgerEntry {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "running_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal runningBalance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry() {
        this.runningBalance = BigDecimal.ZERO;
        this.currency = "KES";
        this.createdAt = Instant.now();
    }

    public LedgerEntry(UUID id, LedgerTransaction transaction, Account account, EntryType entryType,
                       BigDecimal amount, BigDecimal runningBalance, String currency, Instant createdAt) {
        this.id = id;
        this.transaction = transaction;
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
        this.runningBalance = runningBalance != null ? runningBalance : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "KES";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static LedgerEntryBuilder builder() {
        return new LedgerEntryBuilder();
    }

    public static class LedgerEntryBuilder {
        private UUID id;
        private LedgerTransaction transaction;
        private Account account;
        private EntryType entryType;
        private BigDecimal amount;
        private BigDecimal runningBalance = BigDecimal.ZERO;
        private String currency = "KES";
        private Instant createdAt;

        public LedgerEntryBuilder id(UUID id) { this.id = id; return this; }
        public LedgerEntryBuilder transaction(LedgerTransaction transaction) { this.transaction = transaction; return this; }
        public LedgerEntryBuilder account(Account account) { this.account = account; return this; }
        public LedgerEntryBuilder entryType(EntryType entryType) { this.entryType = entryType; return this; }
        public LedgerEntryBuilder direction(EntryType direction) { this.entryType = direction; return this; }
        public LedgerEntryBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public LedgerEntryBuilder runningBalance(BigDecimal runningBalance) { this.runningBalance = runningBalance; return this; }
        public LedgerEntryBuilder currency(String currency) { this.currency = currency; return this; }
        public LedgerEntryBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public LedgerEntry build() {
            return new LedgerEntry(id, transaction, account, entryType, amount, runningBalance, currency, createdAt);
        }
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (runningBalance == null) {
            runningBalance = BigDecimal.ZERO;
        }
        if (currency == null) {
            currency = "KES";
        }
    }

    public EntryType getDirection() {
        return entryType;
    }

    public void setDirection(EntryType direction) {
        this.entryType = direction;
    }
}
