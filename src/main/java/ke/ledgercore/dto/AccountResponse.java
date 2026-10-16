package ke.ledgercore.dto;

import ke.ledgercore.model.Account;
import ke.ledgercore.model.AccountStatus;
import ke.ledgercore.model.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private UUID id;
    private UUID userId;
    private UUID customerId;
    private String accountNumber;
    private AccountType type;
    private String currency;
    private BigDecimal balance;
    private AccountStatus status;
    private Long version;
    private Instant createdAt;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .userId(account.getUser() != null ? account.getUser().getId() : null)
                .customerId(account.getCustomerId())
                .accountNumber(account.getAccountNumber())
                .type(account.getType())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .status(account.getStatus())
                .version(account.getVersion())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
