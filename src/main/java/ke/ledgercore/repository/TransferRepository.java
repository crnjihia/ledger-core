package ke.ledgercore.repository;

import ke.ledgercore.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    // Additional query methods can be added here
}
