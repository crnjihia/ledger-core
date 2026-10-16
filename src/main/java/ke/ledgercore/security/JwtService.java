package ke.ledgercore.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import ke.ledgercore.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtService {

    @Value("${jwt.secret:c3VwZXItc2VjdXJlLWtleS1mb3ItbGVkZ2VyLWNvcmUtYmFua2luZy1zeXN0ZW0tMjU2LWJpdHM=}")
    private String secret;

    private Key key;
    private final long jwtExpirationMs = 15 * 60 * 1000L; // 15 minutes
    private final long refreshExpirationDays = 7L; // 7 days

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // In-memory fallback if Redis is not configured in local tests
    private final Map<String, String> localRefreshTokenStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                keyBytes = md.digest(keyBytes);
            } catch (Exception ex) {
                keyBytes = Arrays.copyOf(keyBytes, 32);
            }
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("role", user.getRole().name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        storeRefreshToken(refreshToken, user.getUsername());
        return refreshToken;
    }

    public TokenPair rotateRefreshToken(String oldRefreshToken, User user) {
        String username = validateRefreshToken(oldRefreshToken);
        if (username == null || !username.equals(user.getUsername())) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }
        revokeRefreshToken(oldRefreshToken);
        String newAccessToken = generateToken(user);
        String newRefreshToken = generateRefreshToken(user);
        return new TokenPair(newAccessToken, newRefreshToken);
    }

    public String validateRefreshToken(String token) {
        if (token == null) return null;
        if (redisTemplate != null) {
            try {
                return redisTemplate.opsForValue().get("refresh_token:" + token);
            } catch (Exception ignored) {}
        }
        return localRefreshTokenStore.get(token);
    }

    public void revokeRefreshToken(String token) {
        if (token == null) return;
        if (redisTemplate != null) {
            try {
                redisTemplate.delete("refresh_token:" + token);
            } catch (Exception ignored) {}
        }
        localRefreshTokenStore.remove(token);
    }

    private void storeRefreshToken(String token, String username) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(
                        "refresh_token:" + token,
                        username,
                        Duration.ofDays(refreshExpirationDays)
                );
            } catch (Exception ignored) {}
        }
        localRefreshTokenStore.put(token, username);
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}
