package ke.ledgercore.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class DisbursementResponse {
    private UUID id;
    private UUID fromAccountId;
    private String msisdn;
    private BigDecimal amount;
    private String remarks;
    private String status;
    private String receipt;

    public DisbursementResponse() {}

    public DisbursementResponse(UUID id, UUID fromAccountId, String msisdn, BigDecimal amount, String remarks, String status, String receipt) {
        this.id = id;
        this.fromAccountId = fromAccountId;
        this.msisdn = msisdn;
        this.amount = amount;
        this.remarks = remarks;
        this.status = status;
        this.receipt = receipt;
    }
    // getters
    public UUID getId() {return id;}
    public UUID getFromAccountId() {return fromAccountId;}
    public String getMsisdn() {return msisdn;}
    public BigDecimal getAmount() {return amount;}
    public String getRemarks() {return remarks;}
    public String getStatus() {return status;}
    public String getReceipt() {return receipt;}

    // setters
    public void setId(UUID id) { this.id = id; }
    public void setFromAccountId(UUID fromAccountId) { this.fromAccountId = fromAccountId; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public void setStatus(String status) { this.status = status; }
    public void setReceipt(String receipt) { this.receipt = receipt; }
}
