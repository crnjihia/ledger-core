package ke.ledgercore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import ke.ledgercore.model.IdempotencyRecord;
import ke.ledgercore.repository.IdempotencyRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Counter idempotencyHitsCounter;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.idempotencyHitsCounter = meterRegistry != null
                ? meterRegistry.counter("ledger.idempotency.hits")
                : null;
    }

    public String hash(Object obj) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = objectMapper.writeValueAsBytes(obj);
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public <T> ResponseEntity<T> handle(UUID key, Object requestObject, Supplier<ResponseEntity<T>> executor) {
        return handle(key != null ? key.toString() : null, requestObject, executor);
    }

    @Transactional
    public <T> ResponseEntity<T> handle(String key, Object requestObject, Supplier<ResponseEntity<T>> executor) {
        if (key == null || key.isBlank()) {
            return executor.get();
        }

        String requestHash = hash(requestObject);
        String cacheKey = "idempotency:" + key;

        // 1. Fast path: Redis cache
        if (redisTemplate != null) {
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    int firstSep = cached.indexOf('|');
                    if (firstSep != -1) {
                        String cachedHash = cached.substring(0, firstSep);
                        if (!cachedHash.equals(requestHash)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reuse with different payload");
                        }
                        int secondSep = cached.indexOf('|', firstSep + 1);
                        if (secondSep != -1) {
                            if (idempotencyHitsCounter != null) {
                                idempotencyHitsCounter.increment();
                            }
                            int status = Integer.parseInt(cached.substring(firstSep + 1, secondSep));
                            String bodyStr = cached.substring(secondSep + 1);
                            return buildCachedResponse(status, bodyStr);
                        }
                    }
                }
            } catch (ResponseStatusException rse) {
                throw rse;
            } catch (Exception ignored) {
                // Redis fail-open to DB
            }
        }

        // 2. Database level atomic claim
        Instant expiresAt = Instant.now().plus(Duration.ofHours(24));
        int claimed = repository.claim(key, requestHash, expiresAt);
        if (claimed == 1) {
            // First time processing
            try {
                ResponseEntity<T> response = executor.get();
                String responseBody = response.hasBody() ? objectMapper.writeValueAsString(response.getBody()) : null;
                int statusCode = response.getStatusCode().value();
                repository.setResponse(key, statusCode, responseBody);

                // Cache in Redis (TTL 24h)
                if (redisTemplate != null) {
                    try {
                        String cacheValue = requestHash + "|" + statusCode + "|" + (responseBody != null ? responseBody : "");
                        redisTemplate.opsForValue().set(cacheKey, cacheValue, Duration.ofHours(24));
                    } catch (Exception ignored) {}
                }
                return response;
            } catch (RuntimeException ex) {
                if (ex instanceof ResponseStatusException rse && rse.getStatusCode().is5xxServerError()) {
                    repository.deleteById(key);
                    deleteFromRedis(cacheKey);
                } else if (ex instanceof DataAccessException) {
                    repository.deleteById(key);
                    deleteFromRedis(cacheKey);
                }
                throw ex;
            } catch (Exception ex) {
                repository.deleteById(key);
                deleteFromRedis(cacheKey);
                throw new RuntimeException(ex);
            }
        } else {
            // Key already exists
            IdempotencyRecord existing = repository.findById(key)
                    .orElseThrow(() -> new IllegalStateException("Idempotency record missing"));

            if (!existing.getRequestHash().equals(requestHash)) {
                // Same key + different payload: return 409 Conflict
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reuse with different payload");
            }

            if (Boolean.TRUE.equals(existing.getCompleted())) {
                if (idempotencyHitsCounter != null) {
                    idempotencyHitsCounter.increment();
                }
                String responseBody = existing.getResponseBody();
                int status = existing.getStatusCode() != null ? existing.getStatusCode() : HttpStatus.OK.value();

                // Repopulate cache
                if (redisTemplate != null) {
                    try {
                        String cacheValue = requestHash + "|" + status + "|" + (responseBody != null ? responseBody : "");
                        redisTemplate.opsForValue().set(cacheKey, cacheValue, Duration.ofHours(24));
                    } catch (Exception ignored) {}
                }

                return buildCachedResponse(status, responseBody);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is currently being processed with this idempotency key");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> buildCachedResponse(int status, String bodyStr) {
        if (bodyStr == null || bodyStr.isEmpty()) {
            return ResponseEntity.status(status).build();
        }
        if (bodyStr.startsWith("\"") && bodyStr.endsWith("\"") && bodyStr.length() > 2) {
            String unquoted = bodyStr.substring(1, bodyStr.length() - 1);
            try {
                UUID uuid = UUID.fromString(unquoted);
                return (ResponseEntity<T>) ResponseEntity.status(status).body(uuid);
            } catch (Exception ignored) {}
        }
        try {
            T body = objectMapper.readValue(bodyStr, (Class<T>) Object.class);
            return ResponseEntity.status(status).body(body);
        } catch (Exception e) {
            return (ResponseEntity<T>) ResponseEntity.status(status).body((T) bodyStr);
        }
    }

    private void deleteFromRedis(String key) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception ignored) {}
        }
    }
}
