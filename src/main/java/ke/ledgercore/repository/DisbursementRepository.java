package ke.ledgercore.repository;

import ke.ledgercore.model.Disbursement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {
    @Query("SELECT SUM(d.amount) FROM Disbursement d WHERE d.fromAccount.id = :accountId AND d.createdAt >= :start AND d.createdAt <= :end")
    java.math.BigDecimal sumAmountForAccountAndPeriod(@Param("accountId") UUID accountId, @Param("start") Instant start, @Param("end") Instant end);
}
