package ke.ledgercore.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public class PostingEntryDto {
    @NotNull
    private UUID accountId;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private ke.ledgercore.model.EntryType entryType;

    // getters and setters
    public UUID getAccountId() {return accountId;}
    public void setAccountId(UUID accountId) {this.accountId = accountId;}
    public BigDecimal getAmount() {return amount;}
    public void setAmount(BigDecimal amount) {this.amount = amount;}
    public ke.ledgercore.model.EntryType getEntryType() {return entryType;}
    public void setEntryType(ke.ledgercore.model.EntryType entryType) {this.entryType = entryType;}
}
