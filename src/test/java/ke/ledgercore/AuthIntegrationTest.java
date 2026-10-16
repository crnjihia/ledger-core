package ke.ledgercore;

import ke.ledgercore.controller.AuthController.LoginRequest;
import ke.ledgercore.controller.AuthController.LoginResponse;
import ke.ledgercore.model.User;
import ke.ledgercore.model.UserRole;
import ke.ledgercore.repository.UserRepository;
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

class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(admin);

        // login to obtain token
        LoginRequest login = new LoginRequest("admin", "admin123");
        ResponseEntity<LoginResponse> resp = restTemplate.postForEntity("/api/v1/auth/login", login, LoginResponse.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        adminToken = resp.getBody().getToken();
    }

    @Test
    void loginSuccess() {
        LoginRequest login = new LoginRequest("admin", "admin123");
        ResponseEntity<LoginResponse> resp = restTemplate.postForEntity("/api/v1/auth/login", login, LoginResponse.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertNotNull(resp.getBody().getToken());
    }

    @Test
    void loginFailure() {
        LoginRequest login = new LoginRequest("admin", "wrongpass");
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/login", login, String.class);
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void accessProtectedWithoutToken() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/api/v1/secure/ping", String.class);
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void accessProtectedWithAdminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/secure/ping", HttpMethod.GET, request, String.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertEquals("secure pong", resp.getBody());
    }

    @Test
    void accessProtectedWithInsufficientRole() {
        // create a CUSTOMER user
        User cust = User.builder()
                .username("cust")
                .passwordHash(passwordEncoder.encode("cust123"))
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(cust);
        // login as customer
        LoginRequest login = new LoginRequest("cust", "cust123");
        ResponseEntity<LoginResponse> loginResp = restTemplate.postForEntity("/api/v1/auth/login", login, LoginResponse.class);
        Assertions.assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        String custToken = loginResp.getBody().getToken();
        // attempt to access admin endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(custToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/secure/ping", HttpMethod.GET, request, String.class);
        Assertions.assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }
}
