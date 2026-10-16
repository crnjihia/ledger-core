package ke.ledgercore.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class StatementEntryDTO {
    private UUID id;
    private UUID transactionId;
    private BigDecimal amount;
    private Instant createdAt;
    private BigDecimal balanceAfter;

    public StatementEntryDTO() {
    }

    public StatementEntryDTO(UUID id, UUID transactionId, BigDecimal amount, Instant createdAt, BigDecimal balanceAfter) {
        this.id = id;
        this.transactionId = transactionId;
        this.amount = amount;
        this.createdAt = createdAt;
        this.balanceAfter = balanceAfter;
    }

    // getters & setters
    public UUID getId() {return id;}
    public void setId(UUID id) {this.id = id;}
    public UUID getTransactionId() {return transactionId;}
    public void setTransactionId(UUID transactionId) {this.transactionId = transactionId;}
    public BigDecimal getAmount() {return amount;}
    public void setAmount(BigDecimal amount) {this.amount = amount;}
    public Instant getCreatedAt() {return createdAt;}
    public void setCreatedAt(Instant createdAt) {this.createdAt = createdAt;}
    public BigDecimal getBalanceAfter() {return balanceAfter;}
    public void setBalanceAfter(BigDecimal balanceAfter) {this.balanceAfter = balanceAfter;}
}
