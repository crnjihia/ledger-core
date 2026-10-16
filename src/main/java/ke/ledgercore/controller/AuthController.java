package ke.ledgercore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ke.ledgercore.model.User;
import ke.ledgercore.repository.UserRepository;
import ke.ledgercore.security.JwtService;
import ke.ledgercore.service.AuditService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Authentication and token lifecycle endpoints")
@RestController
@RequestMapping({"/auth", "/api/v1/auth"})
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Operation(summary = "Login and obtain JWT tokens", description = "Returns access token (15 min) and refresh token (7 days)")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException ex) {
            auditService.audit("LOGIN", "LOGIN_FAILURE", "FAILURE", null, request.getUsername(), "UNKNOWN", null, null, "Login failed: bad credentials");
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        auditService.audit("LOGIN", "LOGIN_SUCCESS", "SUCCESS", user.getId(), user.getUsername(), user.getRole().name(), null, null, "User logged in successfully");

        return ResponseEntity.ok(LoginResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900L)
                .role(user.getRole().name())
                .build());
    }

    @Operation(summary = "Rotate refresh token", description = "Exchanges a valid refresh token for a new access token and rotated refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String username = jwtService.validateRefreshToken(request.getRefreshToken());
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        JwtService.TokenPair pair = jwtService.rotateRefreshToken(request.getRefreshToken(), user);

        return ResponseEntity.ok(LoginResponse.builder()
                .token(pair.accessToken())
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .tokenType("Bearer")
                .expiresIn(900L)
                .role(user.getRole().name())
                .build());
    }

    @Operation(summary = "Logout and revoke refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            jwtService.revokeRefreshToken(request.getRefreshToken());
        }
        return ResponseEntity.ok().build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshRequest {
        private String refreshToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginResponse {
        private String token;
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn;
        private String role;

        public LoginResponse(String token) {
            this.token = token;
            this.accessToken = token;
            this.tokenType = "Bearer";
            this.expiresIn = 900L;
        }
    }
}
