package ke.ledgercore.service;

import ke.ledgercore.dto.PostingEntryDto;
import ke.ledgercore.dto.PostingRequest;
import ke.ledgercore.model.EntryType;
import ke.ledgercore.model.User;
import ke.ledgercore.model.UserRole;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
class LedgerInsufficientFundsTest {

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

    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
        User cust = User.builder().username("cust").passwordHash("hash").role(UserRole.CUSTOMER).build();
        cust = userRepository.save(cust);
        var acc = ke.ledgercore.model.Account.builder()
                .user(cust)
                .accountNumber("1111111111")
                .balance(new BigDecimal("50"))
                .status(ke.ledgercore.model.AccountStatus.ACTIVE)
                .build();
        acc = accountRepository.save(acc);
        accountId = acc.getId();
    }

    @Test
    void insufficientFundsShouldThrow() {
        PostingEntryDto debit = new PostingEntryDto();
        debit.setAccountId(accountId);
        debit.setAmount(new BigDecimal("100")); // greater than balance
        debit.setEntryType(EntryType.DEBIT);
        PostingEntryDto credit = new PostingEntryDto();
        credit.setAccountId(accountId);
        credit.setAmount(new BigDecimal("100"));
        credit.setEntryType(EntryType.CREDIT);
        PostingRequest req = new PostingRequest();
        req.setEntries(Arrays.asList(debit, credit));
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> ledgerService.post(req));
    }
}
