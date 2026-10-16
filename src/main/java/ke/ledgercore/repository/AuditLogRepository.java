package ke.ledgercore.repository;

import ke.ledgercore.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:entityId IS NULL OR a.entityId = :entityId) AND " +
           "(:actorId IS NULL OR a.actorId = :actorId) AND " +
           "(:from IS NULL OR a.timestamp >= :from) AND " +
           "(:to IS NULL OR a.timestamp <= :to) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findFiltered(
            @Param("entityId") String entityId,
            @Param("actorId") String actorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
