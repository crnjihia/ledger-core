package ke.ledgercore.dto;

import ke.ledgercore.model.EntryType;
import ke.ledgercore.model.TransactionStatus;
import ke.ledgercore.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private UUID id;
    private String reference;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private String currency;
    private UUID debitAccountId;
    private UUID creditAccountId;
    private String idempotencyKey;
    private String description;
    private Instant createdAt;
    private Instant postedAt;
    private boolean reversed;
    private boolean reversal;
    private List<EntryResponse> entries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EntryResponse {
        private UUID id;
        private UUID accountId;
        private EntryType direction;
        private BigDecimal amount;
        private BigDecimal runningBalance;
        private String currency;
    }
}
