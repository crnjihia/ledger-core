package ke.ledgercore.dto;

import ke.ledgercore.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

public class AccountStatusUpdateRequest {
    @NotNull
    private AccountStatus status;

    public AccountStatus getStatus() {
        return status;
    }
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}
