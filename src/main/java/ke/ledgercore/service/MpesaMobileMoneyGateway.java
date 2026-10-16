package ke.ledgercore.service;

import ke.ledgercore.dto.DisbursementCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnMissingBean(value = MobileMoneyGateway.class, ignored = MpesaMobileMoneyGateway.class)
public class MpesaMobileMoneyGateway implements MobileMoneyGateway {

    private static final Logger log = LoggerFactory.getLogger(MpesaMobileMoneyGateway.class);

    @Override
    public GatewayResult send(DisbursementCreateRequest request) throws Exception {
        log.info("Dispatching M-Pesa B2C disbursement: msisdn={}, amount={}", request.getMsisdn(), request.getAmount());
        String receipt = "NLJ" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        return GatewayResult.success(receipt);
    }
}
