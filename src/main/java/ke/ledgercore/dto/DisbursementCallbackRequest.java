package ke.ledgercore.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DisbursementCallbackRequest {
    @JsonAlias({"disbursementId", "id", "TransactionID", "transactionId"})
    private UUID disbursementId;

    @JsonAlias({"result", "status", "ResultDesc", "resultDesc"})
    private String result;

    @JsonAlias({"resultCode", "ResultCode"})
    private Integer resultCode;

    @JsonAlias({"receipt", "mpesaReceiptNumber", "TransactionReceipt"})
    private String receipt;

    public boolean isSuccessful() {
        if (resultCode != null && resultCode == 0) return true;
        if (result != null && ("SUCCESS".equalsIgnoreCase(result) || "0".equals(result))) return true;
        return false;
    }
}
