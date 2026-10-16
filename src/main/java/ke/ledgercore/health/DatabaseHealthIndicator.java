package ke.ledgercore.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component("databaseHealthIndicator")
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            boolean valid = stmt.execute("SELECT 1");
            if (valid) {
                return Health.up()
                        .withDetail("database", conn.getMetaData().getDatabaseProductName())
                        .withDetail("version", conn.getMetaData().getDatabaseProductVersion())
                        .build();
            }
            return Health.down().withDetail("reason", "Query execution returned false").build();
        } catch (Exception e) {
            return Health.down(e).withDetail("error", e.getMessage()).build();
        }
    }
}
