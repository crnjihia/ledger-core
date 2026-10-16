package ke.ledgercore;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.util.UUID;

class LedgerIntegrationTest extends BaseIntegrationTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void migrationsApplyAndImmutabilityEnforced() {
        // Verify that the ledger_transactions table exists by counting rows (should be 0 initially)
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_transactions", Integer.class);
        Assertions.assertNotNull(count);
        Assertions.assertEquals(0, count);

        // Insert a user and an account for FK constraints
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, role, created_at, version) VALUES (?, ?, ?, ?, now(), 0)",
                userId, "testuser", "hash", "CUSTOMER"
        );
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO accounts (id, user_id, account_number, balance, status, version, created_at) VALUES (?, ?, ?, ?, ?, 0, now())",
                accountId, userId, "ACC123", 1000, "ACTIVE"
        );

        // Insert a transaction and an entry
        UUID txId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ledger_transactions (id, transaction_number, status, created_at, version) VALUES (?, ?, ?, now(), 0)",
                txId, "TX001", "PENDING"
        );
        UUID entryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ledger_entries (id, transaction_id, account_id, amount, entry_type, created_at) VALUES (?, ?, ?, ?, 'DEBIT', now())",
                entryId, txId, accountId, 100
        );

        // Attempt to UPDATE ledger_entries – should raise exception from trigger
        Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
            jdbcTemplate.update(
                    "UPDATE ledger_entries SET amount = 200 WHERE id = ?",
                    entryId
            );
        });
        Assertions.assertTrue(exception.getMessage().contains("ledger_entries are immutable"),
                "Expected immutability trigger exception, got: " + exception.getMessage());
    }
}
