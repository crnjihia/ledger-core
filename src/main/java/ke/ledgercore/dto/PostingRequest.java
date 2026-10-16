package ke.ledgercore.dto;

import jakarta.validation.constraints.NotEmpty;
import ke.ledgercore.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostingRequest {
    @NotEmpty
    private List<PostingEntryDto> entries;

    private TransactionType type = TransactionType.TRANSFER;
    private String description;
    private String idempotencyKey;
}
