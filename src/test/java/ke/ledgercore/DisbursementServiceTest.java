package ke.ledgercore;

import ke.ledgercore.dto.DisbursementCreateRequest;
import ke.ledgercore.dto.DisbursementResponse;
import ke.ledgercore.model.Account;
import ke.ledgercore.model.DisbursementStatus;
import ke.ledgercore.model.User;
import ke.ledgercore.model.UserRole;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.UserRepository;
import ke.ledgercore.security.JwtService;
import ke.ledgercore.service.GatewayResult;
import ke.ledgercore.service.MobileMoneyGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

class DisbursementServiceTest extends BaseIntegrationTest {

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

    @MockBean
    private MobileMoneyGateway mobileMoneyGateway;

    private String adminToken;
    private UUID settlementAccountId;
    private UUID sourceAccountId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        // admin user
        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(UserRole.ADMIN)
                .build();
        final User savedAdmin = userRepository.save(admin);
        // login for token
        var login = new ke.ledgercore.controller.AuthController.LoginRequest("admin", "admin123");
        ResponseEntity<ke.ledgercore.controller.AuthController.LoginResponse> resp = restTemplate.postForEntity("/api/v1/auth/login", login, ke.ledgercore.controller.AuthController.LoginResponse.class);
        adminToken = resp.getBody().getToken();
        // create settlement account and source account
        Account settlement = accountRepository.findAll().stream()
                .filter(a -> "SETTLEMENT".equals(a.getAccountNumber()))
                .findFirst()
                .orElseGet(() -> {
                    Account s = Account.builder()
                            .user(savedAdmin)
                            .accountNumber("SETTLEMENT")
                            .type(ke.ledgercore.model.AccountType.SUSPENSE)
                            .currency("KES")
                            .balance(BigDecimal.ZERO)
                            .status(ke.ledgercore.model.AccountStatus.ACTIVE)
                            .build();
                    return accountRepository.save(s);
                });
        settlementAccountId = settlement.getId();
        // create a source account for admin with balance 1000
        Account src = Account.builder()
                .user(savedAdmin)
                .accountNumber("9999999999")
                .balance(new BigDecimal("1000"))
                .status(ke.ledgercore.model.AccountStatus.ACTIVE)
                .build();
        src = accountRepository.save(src);
        sourceAccountId = src.getId();
    }

    @Test
    void successDisbursement() throws Exception {
        // Mock gateway to succeed
        Mockito.when(mobileMoneyGateway.send(Mockito.any()))
                .thenReturn(GatewayResult.success("REC123456"));
        DisbursementCreateRequest req = new DisbursementCreateRequest();
        req.setFromAccountId(sourceAccountId);
        req.setMsisdn("+254712345678");
        req.setAmount(new BigDecimal("100"));
        req.setRemarks("payout");
        HttpHeaders hdr = new HttpHeaders();
        hdr.setBearerAuth(adminToken);
        hdr.setContentType(MediaType.APPLICATION_JSON);
        hdr.set("Idempotency-Key", UUID.randomUUID().toString());
        HttpEntity<DisbursementCreateRequest> httpReq = new HttpEntity<>(req, hdr);
        ResponseEntity<DisbursementResponse> resp = restTemplate.exchange("/api/v1/disbursements", HttpMethod.POST, httpReq, DisbursementResponse.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        DisbursementResponse body = resp.getBody();
        Assertions.assertEquals(DisbursementStatus.CONFIRMED.name(), body.getStatus());
        Assertions.assertEquals("REC123456", body.getReceipt());
        // verify balances: source decreased by 100, settlement increased by 100
        Account src = accountRepository.findById(sourceAccountId).orElseThrow();
        Account settlement = accountRepository.findById(settlementAccountId).orElseThrow();
        Assertions.assertEquals(0, src.getBalance().compareTo(new BigDecimal("900")));
        Assertions.assertEquals(0, settlement.getBalance().compareTo(new BigDecimal("100")));
    }

    @Test
    void failureDisbursementTriggersReversal() throws Exception {
        Mockito.when(mobileMoneyGateway.send(Mockito.any()))
                .thenReturn(GatewayResult.failure("Insufficient funds at provider"));
        DisbursementCreateRequest req = new DisbursementCreateRequest();
        req.setFromAccountId(sourceAccountId);
        req.setMsisdn("+254712345678");
        req.setAmount(new BigDecimal("200"));
        req.setRemarks("payout");
        HttpHeaders hdr = new HttpHeaders();
        hdr.setBearerAuth(adminToken);
        hdr.setContentType(MediaType.APPLICATION_JSON);
        hdr.set("Idempotency-Key", UUID.randomUUID().toString());
        HttpEntity<DisbursementCreateRequest> httpReq = new HttpEntity<>(req, hdr);
        ResponseEntity<DisbursementResponse> resp = restTemplate.exchange("/api/v1/disbursements", HttpMethod.POST, httpReq, DisbursementResponse.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        DisbursementResponse body = resp.getBody();
        Assertions.assertEquals(DisbursementStatus.FAILED.name(), body.getStatus());
        // source balance should be restored, settlement unchanged
        Account src = accountRepository.findById(sourceAccountId).orElseThrow();
        Account settlement = accountRepository.findById(settlementAccountId).orElseThrow();
        Assertions.assertEquals(0, src.getBalance().compareTo(new BigDecimal("1000")));
        Assertions.assertEquals(0, settlement.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void timeoutThenCallbackSuccess() throws Exception {
        // Configure gateway to timeout (throw TimeoutException)
        Mockito.when(mobileMoneyGateway.send(Mockito.any()))
                .thenThrow(new TimeoutException("gateway timeout"));
        DisbursementCreateRequest req = new DisbursementCreateRequest();
        req.setFromAccountId(sourceAccountId);
        req.setMsisdn("+254711122333");
        req.setAmount(new BigDecimal("150"));
        req.setRemarks("timeout test");
        HttpHeaders hdr = new HttpHeaders();
        hdr.setBearerAuth(adminToken);
        hdr.setContentType(MediaType.APPLICATION_JSON);
        String idemKey = UUID.randomUUID().toString();
        hdr.set("Idempotency-Key", idemKey);
        HttpEntity<DisbursementCreateRequest> httpReq = new HttpEntity<>(req, hdr);
        ResponseEntity<DisbursementResponse> resp = restTemplate.exchange("/api/v1/disbursements", HttpMethod.POST, httpReq, DisbursementResponse.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertEquals(DisbursementStatus.SENT.name(), resp.getBody().getStatus());
        UUID disbId = resp.getBody().getId();
        // Simulate callback success
        var callback = new ke.ledgercore.dto.DisbursementCallbackRequest();
        callback.setDisbursementId(disbId);
        callback.setResult("SUCCESS");
        callback.setReceipt("CALLBACKREC123");
        HttpHeaders cbHdr = new HttpHeaders();
        cbHdr.setContentType(MediaType.APPLICATION_JSON);
        cbHdr.set("X-Callback-Secret", "REPLACE_ME");
        HttpEntity<ke.ledgercore.dto.DisbursementCallbackRequest> cbReq = new HttpEntity<>(callback, cbHdr);
        ResponseEntity<Void> cbResp = restTemplate.exchange("/api/v1/disbursements/mpesa/callback", HttpMethod.POST, cbReq, Void.class);
        Assertions.assertEquals(HttpStatus.OK, cbResp.getStatusCode());
        // Verify updated status
        HttpHeaders getHdr = new HttpHeaders();
        getHdr.setBearerAuth(adminToken);
        ResponseEntity<DisbursementResponse> getResp = restTemplate.exchange("/api/v1/disbursements/" + disbId, HttpMethod.GET, new HttpEntity<>(null, getHdr), DisbursementResponse.class);
        Assertions.assertEquals(HttpStatus.OK, getResp.getStatusCode());
        Assertions.assertEquals(DisbursementStatus.CONFIRMED.name(), getResp.getBody().getStatus());
        Assertions.assertEquals("CALLBACKREC123", getResp.getBody().getReceipt());
    }

    @Test
    void duplicateIdempotentRequestCallsGatewayOnce() throws Exception {
        Mockito.when(mobileMoneyGateway.send(Mockito.any()))
                .thenReturn(GatewayResult.success("RECEIPT1"));
        DisbursementCreateRequest req = new DisbursementCreateRequest();
        req.setFromAccountId(sourceAccountId);
        req.setMsisdn("+254700000001");
        req.setAmount(new BigDecimal("50"));
        req.setRemarks("duplicate test");
        HttpHeaders hdr = new HttpHeaders();
        hdr.setBearerAuth(adminToken);
        hdr.setContentType(MediaType.APPLICATION_JSON);
        String idemKey = UUID.randomUUID().toString();
        hdr.set("Idempotency-Key", idemKey);
        HttpEntity<DisbursementCreateRequest> httpReq1 = new HttpEntity<>(req, hdr);
        ResponseEntity<DisbursementResponse> resp1 = restTemplate.exchange("/api/v1/disbursements", HttpMethod.POST, httpReq1, DisbursementResponse.class);
        // second request with same idempotency key and same payload
        HttpEntity<DisbursementCreateRequest> httpReq2 = new HttpEntity<>(req, hdr);
        ResponseEntity<DisbursementResponse> resp2 = restTemplate.exchange("/api/v1/disbursements", HttpMethod.POST, httpReq2, DisbursementResponse.class);
        Assertions.assertEquals(resp1.getBody().getId(), resp2.getBody().getId());
        Mockito.verify(mobileMoneyGateway, Mockito.times(1)).send(Mockito.any());
    }
}
