package ke.ledgercore;

import ke.ledgercore.dto.TransferCreateRequest;
import ke.ledgercore.dto.TransferResponse;
import ke.ledgercore.model.Account;
import ke.ledgercore.model.User;
import ke.ledgercore.model.UserRole;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.UserRepository;
import ke.ledgercore.security.JwtService;
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
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@org.springframework.test.context.TestPropertySource(properties = "ledger.ratelimit.enabled=false")
class TransferConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String adminToken;
    private UUID accountAId;
    private UUID accountBId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        // create admin user
        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(UserRole.ADMIN)
                .build();
        admin = userRepository.save(admin);
        // login for token
        var login = new ke.ledgercore.controller.AuthController.LoginRequest("admin", "admin123");
        ResponseEntity<ke.ledgercore.controller.AuthController.LoginResponse> resp = restTemplate.postForEntity("/api/v1/auth/login", login, ke.ledgercore.controller.AuthController.LoginResponse.class);
        adminToken = resp.getBody().getToken();
        // create two accounts via API
        HttpHeaders hdr = new HttpHeaders();
        hdr.setBearerAuth(adminToken);
        hdr.setContentType(MediaType.APPLICATION_JSON);
        // account A
        var createA = new ke.ledgercore.dto.AccountCreateRequest();
        createA.setUserId(admin.getId());
        HttpEntity<ke.ledgercore.dto.AccountCreateRequest> reqA = new HttpEntity<>(createA, hdr);
        ResponseEntity<ke.ledgercore.dto.AccountResponse> resA = restTemplate.exchange("/api/v1/accounts", HttpMethod.POST, reqA, ke.ledgercore.dto.AccountResponse.class);
        accountAId = resA.getBody().getId();
        // account B
        var createB = new ke.ledgercore.dto.AccountCreateRequest();
        createB.setUserId(admin.getId());
        HttpEntity<ke.ledgercore.dto.AccountCreateRequest> reqB = new HttpEntity<>(createB, hdr);
        ResponseEntity<ke.ledgercore.dto.AccountResponse> resB = restTemplate.exchange("/api/v1/accounts", HttpMethod.POST, reqB, ke.ledgercore.dto.AccountResponse.class);
        accountBId = resB.getBody().getId();
        // set balance of A to 100 (B stays 0)
        Account a = accountRepository.findById(accountAId).orElseThrow();
        a.setBalance(new BigDecimal("100"));
        accountRepository.save(a);
    }

    @Test
    void fiftyThreadsTransferTenFromAToB() throws Exception {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<ResponseEntity<ke.ledgercore.dto.TransferResponse>>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    TransferCreateRequest tr = new TransferCreateRequest();
                    tr.setFromAccountId(accountAId);
                    tr.setToAccountId(accountBId);
                    tr.setAmount(new BigDecimal("10"));
                    tr.setDescription("test transfer");
                    HttpHeaders hdr = new HttpHeaders();
                    hdr.setBearerAuth(adminToken);
                    hdr.setContentType(MediaType.APPLICATION_JSON);
                    hdr.set("Idempotency-Key", UUID.randomUUID().toString());
                    HttpEntity<TransferCreateRequest> req = new HttpEntity<>(tr, hdr);
                    ResponseEntity<ke.ledgercore.dto.TransferResponse> resp = restTemplate.exchange("/api/v1/transfers", HttpMethod.POST, req, ke.ledgercore.dto.TransferResponse.class);
                    return resp;
                } finally {
                    latch.countDown();
                }
            }));
        }
        latch.await();
        executor.shutdown();
        int success = 0;
        for (Future<ResponseEntity<ke.ledgercore.dto.TransferResponse>> f : futures) {
            ResponseEntity<ke.ledgercore.dto.TransferResponse> r = f.get();
            if (r.getStatusCode() == HttpStatus.OK) {
                success++;
            }
        }
        Assertions.assertEquals(10, success, "Exactly 10 transfers should succeed");
        // Verify balances
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(adminToken);
        HttpEntity<Void> authEntity = new HttpEntity<>(authHeaders);
        ResponseEntity<ke.ledgercore.dto.AccountResponse> aResp = restTemplate.exchange("/api/v1/accounts/" + accountAId, HttpMethod.GET, authEntity, ke.ledgercore.dto.AccountResponse.class);
        ResponseEntity<ke.ledgercore.dto.AccountResponse> bResp = restTemplate.exchange("/api/v1/accounts/" + accountBId, HttpMethod.GET, authEntity, ke.ledgercore.dto.AccountResponse.class);
        Assertions.assertEquals(0, aResp.getBody().getBalance().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, bResp.getBody().getBalance().compareTo(new BigDecimal("100")));
        // Reconciliation check
        ResponseEntity<Map> recon = restTemplate.exchange("/api/v1/ledger/reconciliation", HttpMethod.GET, authEntity, Map.class);
        Assertions.assertTrue(recon.getBody().isEmpty(), "Reconciliation should have no discrepancies");
    }

    @Test
    void oppositeTransfersNoDeadlock() throws Exception {
        // reset balances: both accounts start with 100
        Account a = accountRepository.findById(accountAId).orElseThrow();
        a.setBalance(new BigDecimal("100"));
        accountRepository.save(a);
        Account b = accountRepository.findById(accountBId).orElseThrow();
        b.setBalance(new BigDecimal("100"));
        accountRepository.save(b);

        int totalOps = 200; // 100 each direction
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(totalOps);
        for (int i = 0; i < totalOps; i++) {
            final boolean direction = i % 2 == 0; // even: A->B, odd: B->A
            executor.submit(() -> {
                try {
                    TransferCreateRequest tr = new TransferCreateRequest();
                    if (direction) {
                        tr.setFromAccountId(accountAId);
                        tr.setToAccountId(accountBId);
                    } else {
                        tr.setFromAccountId(accountBId);
                        tr.setToAccountId(accountAId);
                    }
                    tr.setAmount(new BigDecimal("1"));
                    tr.setDescription("ping-pong");
                    HttpHeaders hdr = new HttpHeaders();
                    hdr.setBearerAuth(adminToken);
                    hdr.setContentType(MediaType.APPLICATION_JSON);
                    hdr.set("Idempotency-Key", UUID.randomUUID().toString());
                    HttpEntity<TransferCreateRequest> req = new HttpEntity<>(tr, hdr);
                    restTemplate.exchange("/api/v1/transfers", HttpMethod.POST, req, ke.ledgercore.dto.TransferResponse.class);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        // After equal opposite transfers, balances should be unchanged (100 each)
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(adminToken);
        HttpEntity<Void> authEntity = new HttpEntity<>(authHeaders);
        ResponseEntity<ke.ledgercore.dto.AccountResponse> aResp = restTemplate.exchange("/api/v1/accounts/" + accountAId, HttpMethod.GET, authEntity, ke.ledgercore.dto.AccountResponse.class);
        ResponseEntity<ke.ledgercore.dto.AccountResponse> bResp = restTemplate.exchange("/api/v1/accounts/" + accountBId, HttpMethod.GET, authEntity, ke.ledgercore.dto.AccountResponse.class);
        Assertions.assertEquals(0, aResp.getBody().getBalance().compareTo(new BigDecimal("100")));
        Assertions.assertEquals(0, bResp.getBody().getBalance().compareTo(new BigDecimal("100")));
        // Reconciliation must be clean
        ResponseEntity<Map> recon = restTemplate.exchange("/api/v1/ledger/reconciliation", HttpMethod.GET, authEntity, Map.class);
        Assertions.assertTrue(recon.getBody().isEmpty());
    }
}
