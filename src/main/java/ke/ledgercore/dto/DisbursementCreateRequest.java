package ke.ledgercore.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementCreateRequest {
    @JsonAlias({"accountId", "fromAccountId"})
    private UUID fromAccountId;

    @JsonAlias({"phoneNumber", "msisdn", "phone"})
    private String msisdn;

    @NotNull
    private BigDecimal amount;

    private String remarks;

    public UUID getAccountId() {
        return fromAccountId;
    }

    public void setAccountId(UUID accountId) {
        this.fromAccountId = accountId;
    }

    public String getPhoneNumber() {
        return msisdn;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.msisdn = phoneNumber;
    }
}
