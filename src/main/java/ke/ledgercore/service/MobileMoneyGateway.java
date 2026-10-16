package ke.ledgercore.service;

public interface MobileMoneyGateway {
    /**
     * Sends a disbursement request to the mobile money provider.
     * @param request the disbursement details
     * @return a result indicating success, failure, or timeout
     * @throws Exception for transport errors
     */
    GatewayResult send(ke.ledgercore.dto.DisbursementCreateRequest request) throws Exception;
}
