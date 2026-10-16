package ke.ledgercore.service;

public class GatewayResult {
    private final boolean success;
    private final boolean timeout;
    private final String receipt;
    private final String errorMessage;

    private GatewayResult(boolean success, boolean timeout, String receipt, String errorMessage) {
        this.success = success;
        this.timeout = timeout;
        this.receipt = receipt;
        this.errorMessage = errorMessage;
    }

    public static GatewayResult success(String receipt) {
        return new GatewayResult(true, false, receipt, null);
    }

    public static GatewayResult failure(String error) {
        return new GatewayResult(false, false, null, error);
    }

    public static GatewayResult timeout() {
        return new GatewayResult(false, true, null, null);
    }

    public boolean isSuccess() {return success;}
    public boolean isTimeout() {return timeout;}
    public String getReceipt() {return receipt;}
    public String getErrorMessage() {return errorMessage;}
}
