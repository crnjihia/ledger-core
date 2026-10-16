package ke.ledgercore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@org.springframework.test.context.TestPropertySource(properties = "ledger.ratelimit.enabled=false")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    public static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledger")
            .withUsername("ledger")
            .withPassword("ledger");

    public static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        postgres.start();
        try {
            redis.start();
        } catch (Exception ignored) {
            // If local redis is used or container start fails
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        if (redis.isRunning()) {
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", redis::getFirstMappedPort);
            registry.add("spring.redis.host", redis::getHost);
            registry.add("spring.redis.port", redis::getFirstMappedPort);
        }
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE ledger_entries, ledger_transactions, transfers, disbursements, idempotency_records, audit_logs, accounts, users CASCADE;");
    }
}
