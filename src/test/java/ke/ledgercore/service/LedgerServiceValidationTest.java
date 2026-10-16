package ke.ledgercore.service;

import ke.ledgercore.dto.PostingEntryDto;
import ke.ledgercore.dto.PostingRequest;
import ke.ledgercore.model.EntryType;
import ke.ledgercore.model.TransactionStatus;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.LedgerEntryRepository;
import ke.ledgercore.repository.LedgerTransactionRepository;
import ke.ledgercore.repository.UserRepository;
import ke.ledgercore.model.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

@SpringBootTest
@Testcontainers
class LedgerServiceValidationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledger")
            .withUsername("ledger")
            .withPassword("ledger");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private Account account1;
    private Account account2;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
        // create users
        User user1 = User.builder().username("u1").passwordHash("hash").role(UserRole.CUSTOMER).build();
        user1 = userRepository.save(user1);
        User user2 = User.builder().username("u2").passwordHash("hash").role(UserRole.CUSTOMER).build();
        user2 = userRepository.save(user2);
        // create accounts with zero balance
        account1 = Account.builder().user(user1).accountNumber("1234567890").balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();
        account1 = accountRepository.save(account1);
        account2 = Account.builder().user(user2).accountNumber("0987654321").balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();
        account2 = accountRepository.save(account2);
    }

    @Test
    void mismatchedTotalsShouldFail() {
        PostingEntryDto debit = new PostingEntryDto();
        debit.setAccountId(account1.getId());
        debit.setAmount(new BigDecimal("100"));
        debit.setEntryType(EntryType.DEBIT);
        PostingEntryDto credit = new PostingEntryDto();
        credit.setAccountId(account2.getId());
        credit.setAmount(new BigDecimal("90")); // mismatched
        credit.setEntryType(EntryType.CREDIT);
        PostingRequest request = new PostingRequest();
        request.setEntries(Arrays.asList(debit, credit));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ledgerService.post(request));
    }
}
