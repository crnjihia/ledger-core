package ke.ledgercore.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redisHealthIndicator")
public class RedisHealthIndicator implements HealthIndicator {

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        if (redisConnectionFactory == null) {
            return Health.up().withDetail("status", "Redis Connection Factory not configured (standalone mode)").build();
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String ping = connection.ping();
            if ("PONG".equalsIgnoreCase(ping)) {
                return Health.up().withDetail("redis", "Available").withDetail("response", ping).build();
            }
            return Health.down().withDetail("reason", "Unexpected ping response: " + ping).build();
        } catch (Exception e) {
            return Health.down(e).withDetail("error", e.getMessage()).build();
        }
    }
}
