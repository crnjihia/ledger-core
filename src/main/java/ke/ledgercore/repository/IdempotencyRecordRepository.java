package ke.ledgercore.repository;

import ke.ledgercore.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    @Modifying
    @Query(value = "INSERT INTO idempotency_records (idempotency_key, request_hash, completed, created_at, expires_at) VALUES (:key, :hash, false, now(), :expiresAt) ON CONFLICT (idempotency_key) DO NOTHING", nativeQuery = true)
    int claim(@Param("key") String key, @Param("hash") String requestHash, @Param("expiresAt") Instant expiresAt);

    default int claim(UUID key, String requestHash) {
        return claim(key.toString(), requestHash, Instant.now().plusSeconds(86400));
    }

    default int claim(String key, String requestHash) {
        return claim(key, requestHash, Instant.now().plusSeconds(86400));
    }

    @Modifying
    @Query("UPDATE IdempotencyRecord i SET i.responseBody = :body, i.statusCode = :status, i.completed = true WHERE i.idempotencyKey = :key")
    int setResponse(@Param("key") String key, @Param("status") Integer status, @Param("body") String body);

    default int setResponse(UUID key, Integer status, String body) {
        return setResponse(key.toString(), status, body);
    }
}
