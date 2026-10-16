package ke.ledgercore.dto;

import ke.ledgercore.model.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountCreateRequest {
    private UUID userId;
    private UUID customerId;
    private AccountType type = AccountType.SAVINGS;
    private String currency = "KES";

    public UUID getEffectiveCustomerId() {
        return customerId != null ? customerId : userId;
    }
}
