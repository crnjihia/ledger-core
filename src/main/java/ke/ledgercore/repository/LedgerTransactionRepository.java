package ke.ledgercore.repository;

import ke.ledgercore.model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    // Additional query methods can be added here
}
