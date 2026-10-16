package ke.ledgercore;

import ke.ledgercore.dto.PostingEntryDto;
import ke.ledgercore.dto.PostingRequest;
import ke.ledgercore.model.EntryType;
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
import java.util.Arrays;
import java.util.UUID;

class IdempotencyIntegrationTest extends BaseIntegrationTest {

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
        // admin user
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
        // create two accounts for posting
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

        // Seed account A with initial balance so debits do not violate non-negative balance constraint
        var accA = accountRepository.findById(accountAId).orElseThrow();
        accA.setBalance(new BigDecimal("1000"));
        accountRepository.save(accA);
    }

    @Test
    void duplicateIdempotencyKeyReturnsSameResult() {
        UUID key = UUID.randomUUID();
        PostingEntryDto debit = new PostingEntryDto();
        debit.setAccountId(accountAId);
        debit.setAmount(new BigDecimal("100"));
        debit.setEntryType(EntryType.DEBIT);
        PostingEntryDto credit = new PostingEntryDto();
        credit.setAccountId(accountBId);
        credit.setAmount(new BigDecimal("100"));
        credit.setEntryType(EntryType.CREDIT);
        PostingRequest req = new PostingRequest();
        req.setEntries(Arrays.asList(debit, credit));

        HttpHeaders hdr = new HttpHeaders();
        hdr.setBearerAuth(adminToken);
        hdr.setContentType(MediaType.APPLICATION_JSON);
        hdr.set("Idempotency-Key", key.toString());
        HttpEntity<PostingRequest> httpReq = new HttpEntity<>(req, hdr);

        ResponseEntity<UUID> first = restTemplate.exchange("/api/v1/ledger/post", HttpMethod.POST, httpReq, UUID.class);
        Assertions.assertEquals(HttpStatus.OK, first.getStatusCode());
        UUID txId1 = first.getBody();
        // repeat with same key and same payload
        ResponseEntity<UUID> second = restTemplate.exchange("/api/v1/ledger/post", HttpMethod.POST, httpReq, UUID.class);
        Assertions.assertEquals(HttpStatus.OK, second.getStatusCode());
        Assertions.assertEquals(txId1, second.getBody());
    }

    @Test
    void sameKeyDifferentPayloadRejected() {
        UUID key = UUID.randomUUID();
        // first request
        PostingEntryDto d1 = new PostingEntryDto();
        d1.setAccountId(accountAId);
        d1.setAmount(new BigDecimal("50"));
        d1.setEntryType(EntryType.DEBIT);
        PostingEntryDto c1 = new PostingEntryDto();
        c1.setAccountId(accountBId);
        c1.setAmount(new BigDecimal("50"));
        c1.setEntryType(EntryType.CREDIT);
        PostingRequest r1 = new PostingRequest();
        r1.setEntries(Arrays.asList(d1, c1));
        HttpHeaders hdr = new HttpHeaders();
        hdr.setBearerAuth(adminToken);
        hdr.setContentType(MediaType.APPLICATION_JSON);
        hdr.set("Idempotency-Key", key.toString());
        HttpEntity<PostingRequest> e1 = new HttpEntity<>(r1, hdr);
        ResponseEntity<UUID> resp1 = restTemplate.exchange("/api/v1/ledger/post", HttpMethod.POST, e1, UUID.class);
        Assertions.assertEquals(HttpStatus.OK, resp1.getStatusCode());
        // second request with different amount
        PostingEntryDto d2 = new PostingEntryDto();
        d2.setAccountId(accountAId);
        d2.setAmount(new BigDecimal("60"));
        d2.setEntryType(EntryType.DEBIT);
        PostingEntryDto c2 = new PostingEntryDto();
        c2.setAccountId(accountBId);
        c2.setAmount(new BigDecimal("60"));
        c2.setEntryType(EntryType.CREDIT);
        PostingRequest r2 = new PostingRequest();
        r2.setEntries(Arrays.asList(d2, c2));
        HttpEntity<PostingRequest> e2 = new HttpEntity<>(r2, hdr);
        ResponseEntity<String> resp2 = restTemplate.exchange("/api/v1/ledger/post", HttpMethod.POST, e2, String.class);
        Assertions.assertEquals(HttpStatus.CONFLICT, resp2.getStatusCode());
    }
}
