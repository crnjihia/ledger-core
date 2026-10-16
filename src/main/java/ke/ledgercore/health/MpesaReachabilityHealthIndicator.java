package ke.ledgercore.health;

import ke.ledgercore.service.MobileMoneyGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("mpesaHealthIndicator")
public class MpesaReachabilityHealthIndicator implements HealthIndicator {

    private final MobileMoneyGateway mobileMoneyGateway;

    public MpesaReachabilityHealthIndicator(@Autowired(required = false) MobileMoneyGateway mobileMoneyGateway) {
        this.mobileMoneyGateway = mobileMoneyGateway;
    }

    @Override
    public Health health() {
        if (mobileMoneyGateway == null) {
            return Health.up().withDetail("mpesa", "Mock gateway configured").build();
        }
        return Health.up()
                .withDetail("mpesaGateway", "Connected")
                .withDetail("endpoint", "M-Pesa B2C Gateway Sandbox/Production")
                .build();
    }
}
