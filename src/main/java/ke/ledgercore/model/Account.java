package ke.ledgercore.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "account_number"))
@Data
public class Account {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "account_number", nullable = false, length = 30, unique = true)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "account_status")
    @org.hibernate.annotations.JdbcType(org.hibernate.dialect.PostgreSQLEnumJdbcType.class)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Account() {
        this.type = AccountType.SAVINGS;
        this.currency = "KES";
        this.balance = BigDecimal.ZERO;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public Account(UUID id, User user, UUID customerId, String accountNumber, AccountType type,
                   String currency, BigDecimal balance, AccountStatus status, Instant createdAt, Long version) {
        this.id = id;
        this.user = user;
        this.customerId = customerId != null ? customerId : (user != null ? user.getId() : null);
        this.accountNumber = accountNumber;
        this.type = type != null ? type : AccountType.SAVINGS;
        this.currency = currency != null ? currency : "KES";
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.status = status != null ? status : AccountStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.version = version;
    }

    public static AccountBuilder builder() {
        return new AccountBuilder();
    }

    public static class AccountBuilder {
        private UUID id;
        private User user;
        private UUID customerId;
        private String accountNumber;
        private AccountType type = AccountType.SAVINGS;
        private String currency = "KES";
        private BigDecimal balance = BigDecimal.ZERO;
        private AccountStatus status = AccountStatus.ACTIVE;
        private Instant createdAt;
        private Long version;

        public AccountBuilder id(UUID id) { this.id = id; return this; }
        public AccountBuilder user(User user) { this.user = user; return this; }
        public AccountBuilder customerId(UUID customerId) { this.customerId = customerId; return this; }
        public AccountBuilder accountNumber(String accountNumber) { this.accountNumber = accountNumber; return this; }
        public AccountBuilder type(AccountType type) { this.type = type; return this; }
        public AccountBuilder currency(String currency) { this.currency = currency; return this; }
        public AccountBuilder balance(BigDecimal balance) { this.balance = balance; return this; }
        public AccountBuilder status(AccountStatus status) { this.status = status; return this; }
        public AccountBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public AccountBuilder version(Long version) { this.version = version; return this; }

        public Account build() {
            return new Account(id, user, customerId, accountNumber, type, currency, balance, status, createdAt, version);
        }
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        if (type == null) {
            type = AccountType.SAVINGS;
        }
        if (currency == null) {
            currency = "KES";
        }
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
    }

    public UUID getCustomerId() {
        if (customerId != null) {
            return customerId;
        }
        return user != null ? user.getId() : null;
    }
}
