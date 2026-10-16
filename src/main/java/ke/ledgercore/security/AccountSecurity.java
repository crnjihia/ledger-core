package ke.ledgercore.security;

import ke.ledgercore.model.Account;
import ke.ledgercore.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component("accountSecurity")
public class AccountSecurity {

    private final AccountRepository accountRepository;

    public AccountSecurity(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public boolean isOwner(UUID accountId, String username) {
        if (accountId == null || username == null) {
            return false;
        }
        return accountRepository.findById(accountId)
                .map(Account::getUser)
                .filter(user -> user.getUsername().equals(username))
                .isPresent();
    }
}
