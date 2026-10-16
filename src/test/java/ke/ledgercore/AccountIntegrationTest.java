package ke.ledgercore;

import ke.ledgercore.dto.AccountCreateRequest;
import ke.ledgercore.dto.AccountStatusUpdateRequest;
import ke.ledgercore.dto.AccountResponse;
import ke.ledgercore.dto.StatementResponse;
import ke.ledgercore.dto.StatementEntryDTO;
import ke.ledgercore.model.AccountStatus;
import ke.ledgercore.model.User;
import ke.ledgercore.model.UserRole;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.time.Instant;
import java.util.UUID;
import ke.ledgercore.model.TransactionStatus;

class AccountIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ke.ledgercore.repository.LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private ke.ledgercore.repository.LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private java.util.UUID adminId;
    private java.util.UUID customerId;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.HttpComponentsClientHttpRequestFactory());
        // Clean DB
        cleanDatabase();

        // Create admin user
        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(UserRole.ADMIN)
                .build();
        admin = userRepository.save(admin);
        adminId = admin.getId();
        // Create customer user
        User customer = User.builder()
                .username("customer")
                .passwordHash(passwordEncoder.encode("cust123"))
                .role(UserRole.CUSTOMER)
                .build();
        customer = userRepository.save(customer);
        customerId = customer.getId();

        // Obtain JWT tokens
        var adminLogin = new ke.ledgercore.controller.AuthController.LoginRequest("admin", "admin123");
        ResponseEntity<ke.ledgercore.controller.AuthController.LoginResponse> adminResp = restTemplate.postForEntity("/api/v1/auth/login", adminLogin, ke.ledgercore.controller.AuthController.LoginResponse.class);
        adminToken = adminResp.getBody().getToken();
        var custLogin = new ke.ledgercore.controller.AuthController.LoginRequest("customer", "cust123");
        ResponseEntity<ke.ledgercore.controller.AuthController.LoginResponse> custResp = restTemplate.postForEntity("/api/v1/auth/login", custLogin, ke.ledgercore.controller.AuthController.LoginResponse.class);
        customerToken = custResp.getBody().getToken();
    }

    @Test
    void ownershipEnforcement() {
        // Admin creates account for customer
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<AccountCreateRequest> request = new HttpEntity<>(new AccountCreateRequest(){
            { setUserId(customerId); }
        }, headers);
        ResponseEntity<AccountResponse> createResp = restTemplate.exchange("/api/v1/accounts", HttpMethod.POST, request, AccountResponse.class);
        Assertions.assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        UUID accountId = createResp.getBody().getId();

        // Customer lists accounts, should see only its own
        HttpHeaders custHeaders = new HttpHeaders();
        custHeaders.setBearerAuth(customerToken);
        HttpEntity<Void> custReq = new HttpEntity<>(custHeaders);
        ResponseEntity<AccountResponse[]> listResp = restTemplate.exchange("/api/v1/accounts", HttpMethod.GET, custReq, AccountResponse[].class);
        Assertions.assertEquals(HttpStatus.OK, listResp.getStatusCode());
        Assertions.assertEquals(1, listResp.getBody().length);
        Assertions.assertEquals(accountId, listResp.getBody()[0].getId());
    }

    @Test
    void freezeAndCloseRules() {
        // Admin creates account for customer
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<AccountCreateRequest> createReq = new HttpEntity<>(new AccountCreateRequest(){ { setUserId(customerId); } }, headers);
        ResponseEntity<AccountResponse> createResp = restTemplate.exchange("/api/v1/accounts", HttpMethod.POST, createReq, AccountResponse.class);
        UUID accountId = createResp.getBody().getId();

        // Freeze (suspend) the account
        HttpHeaders patchHeaders = new HttpHeaders();
        patchHeaders.setBearerAuth(adminToken);
        patchHeaders.setContentType(MediaType.APPLICATION_JSON);
        AccountStatusUpdateRequest freezeReq = new AccountStatusUpdateRequest();
        freezeReq.setStatus(AccountStatus.SUSPENDED);
        HttpEntity<AccountStatusUpdateRequest> freezeEntity = new HttpEntity<>(freezeReq, patchHeaders);
        ResponseEntity<String> freezeResp = restTemplate.exchange("/api/v1/accounts/" + accountId + "/status", HttpMethod.PATCH, freezeEntity, String.class);
        Assertions.assertEquals(HttpStatus.NO_CONTENT, freezeResp.getStatusCode());

        // Attempt to close with non-zero balance (should fail with 409)
        // Insert a ledger entry to make balance > 0
        // Direct repository manipulation (bypass via service for brevity)
        // Use restTemplate to add a ledger entry is not provided; so we update balance manually
        var account = accountRepository.findById(accountId).orElseThrow();
        account.setBalance(new java.math.BigDecimal("100"));
        accountRepository.save(account);

        AccountStatusUpdateRequest closeReq = new AccountStatusUpdateRequest();
        closeReq.setStatus(AccountStatus.CLOSED);
        HttpEntity<AccountStatusUpdateRequest> closeEntity = new HttpEntity<>(closeReq, patchHeaders);
        ResponseEntity<Void> closeResp = restTemplate.exchange("/api/v1/accounts/" + accountId + "/status", HttpMethod.PATCH, closeEntity, Void.class);
        Assertions.assertEquals(HttpStatus.CONFLICT, closeResp.getStatusCode());
    }

    @Test
    void statementPagination() {
        // Admin creates account for customer
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<AccountCreateRequest> createReq = new HttpEntity<>(new AccountCreateRequest(){ { setUserId(customerId); } }, headers);
        ResponseEntity<AccountResponse> createResp = restTemplate.exchange("/api/v1/accounts", HttpMethod.POST, createReq, AccountResponse.class);
        UUID accountId = createResp.getBody().getId();

        // Insert 25 ledger entries via service (each 10 amount)
        for (int i = 0; i < 25; i++) {
            // Create a transaction
            var tx = new ke.ledgercore.model.LedgerTransaction();
            tx.setTransactionNumber("TX" + i);
            tx.setStatus(TransactionStatus.POSTED);
            tx = ledgerTransactionRepository.save(tx);
            // Create entry
            var entry = new ke.ledgercore.model.LedgerEntry();
            entry.setTransaction(tx);
            entry.setAccount(accountRepository.findById(accountId).orElseThrow());
            entry.setAmount(new java.math.BigDecimal("10"));
            entry.setEntryType(ke.ledgercore.model.EntryType.CREDIT);
            entry.setCreatedAt(Instant.now().plusSeconds(i));
            ledgerEntryRepository.save(entry);
        }

        // Retrieve first page (size 10)
        HttpHeaders stmtHeaders = new HttpHeaders();
        stmtHeaders.setBearerAuth(customerToken);
        HttpEntity<Void> stmtReq = new HttpEntity<>(stmtHeaders);
        ResponseEntity<StatementResponse> stmtResp = restTemplate.exchange("/api/v1/accounts/" + accountId + "/statement?page=0&size=10", HttpMethod.GET, stmtReq, StatementResponse.class);
        Assertions.assertEquals(HttpStatus.OK, stmtResp.getStatusCode());
        Assertions.assertEquals(10, stmtResp.getBody().getEntries().size());
        Assertions.assertEquals(3, stmtResp.getBody().getTotalPages());
    }
}
