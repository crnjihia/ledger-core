package ke.ledgercore.service;

import ke.ledgercore.audit.Auditable;
import ke.ledgercore.dto.DisbursementCallbackRequest;
import ke.ledgercore.dto.DisbursementCreateRequest;
import ke.ledgercore.dto.DisbursementResponse;
import ke.ledgercore.dto.PostingEntryDto;
import ke.ledgercore.dto.PostingRequest;
import ke.ledgercore.model.*;
import ke.ledgercore.repository.AccountRepository;
import ke.ledgercore.repository.DisbursementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
public class DisbursementService {

    private final AccountRepository accountRepository;
    private final DisbursementRepository disbursementRepository;
    private final LedgerService ledgerService;
    private final MobileMoneyGateway mobileMoneyGateway;
    private final AuditService auditService;

    public DisbursementService(
            AccountRepository accountRepository,
            DisbursementRepository disbursementRepository,
            LedgerService ledgerService,
            MobileMoneyGateway mobileMoneyGateway,
            AuditService auditService
    ) {
        this.accountRepository = accountRepository;
        this.disbursementRepository = disbursementRepository;
        this.ledgerService = ledgerService;
        this.mobileMoneyGateway = mobileMoneyGateway;
        this.auditService = auditService;
    }

    @Value("${disbursement.max-per-transaction:250000}")
    private BigDecimal maxPerTransaction = new BigDecimal("250000");

    @Value("${disbursement.daily-limit:1000000}")
    private BigDecimal dailyLimit = new BigDecimal("1000000");

    private static final String SETTLEMENT_ACCOUNT_NUMBER = "SETTLEMENT";

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Auditable(action = "CREATE_DISBURSEMENT", entityType = "Disbursement")
    public DisbursementResponse createDisbursement(DisbursementCreateRequest req, Authentication auth) {
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (req.getAmount().compareTo(maxPerTransaction) > 0) {
            throw new IllegalArgumentException("Amount exceeds per-transaction limit");
        }
        String msisdn = req.getMsisdn() != null ? req.getMsisdn().trim() : "";
        if (msisdn.isBlank()) {
            throw new IllegalArgumentException("Phone number / MSISDN is required");
        }

        Account source = accountRepository.findById(req.getFromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));
        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Source account must be ACTIVE");
        }

        if (auth != null) {
            String username = auth.getName();
            boolean isCustomer = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
            if (isCustomer && !source.getUser().getUsername().equals(username)) {
                throw new IllegalArgumentException("Customers can only disburse from their own accounts");
            }
        }

        Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400 - 1);
        BigDecimal already = disbursementRepository.sumAmountForAccountAndPeriod(source.getId(), startOfDay, endOfDay);
        already = already != null ? already : BigDecimal.ZERO;
        if (already.add(req.getAmount()).compareTo(dailyLimit) > 0) {
            throw new IllegalArgumentException("Daily disbursement limit exceeded");
        }

        String actor = auth != null ? auth.getName() : "system";
        auditService.audit("DISBURSEMENT", "DISBURSEMENT_REQUESTED", "PENDING", source.getId(), actor, "UNKNOWN", null, null, "Disbursement request");

        Account settlement = accountRepository.findAll().stream()
                .filter(a -> SETTLEMENT_ACCOUNT_NUMBER.equals(a.getAccountNumber()))
                .findFirst()
                .orElseGet(() -> {
                    Account newSettlement = Account.builder()
                            .user(source.getUser())
                            .accountNumber(SETTLEMENT_ACCOUNT_NUMBER)
                            .type(AccountType.SUSPENSE)
                            .currency("KES")
                            .balance(BigDecimal.ZERO)
                            .status(AccountStatus.ACTIVE)
                            .build();
                    return accountRepository.save(newSettlement);
                });

        // Build posting entries: debit source, credit settlement
        List<PostingEntryDto> entries = new ArrayList<>();
        PostingEntryDto debit = new PostingEntryDto();
        debit.setAccountId(source.getId());
        debit.setAmount(req.getAmount());
        debit.setEntryType(EntryType.DEBIT);
        entries.add(debit);

        PostingEntryDto credit = new PostingEntryDto();
        credit.setAccountId(settlement.getId());
        credit.setAmount(req.getAmount());
        credit.setEntryType(EntryType.CREDIT);
        entries.add(credit);

        PostingRequest postingRequest = PostingRequest.builder()
                .entries(entries)
                .type(TransactionType.DISBURSEMENT)
                .description(req.getRemarks() != null ? req.getRemarks() : "M-Pesa B2C Disbursement to " + msisdn)
                .build();

        UUID ledgerTxId = ledgerService.post(postingRequest);

        Disbursement disb = Disbursement.builder()
                .fromAccount(source)
                .settlementAccount(settlement)
                .amount(req.getAmount())
                .msisdn(msisdn)
                .remarks(req.getRemarks())
                .status(DisbursementStatus.INITIATED)
                .ledgerTransactionId(ledgerTxId)
                .createdAt(Instant.now())
                .build();
        disb = disbursementRepository.save(disb);

        GatewayResult result = invokeGatewayWithRetries(req);
        if (result.isSuccess()) {
            disb.setStatus(DisbursementStatus.CONFIRMED);
            disb.setReceipt(result.getReceipt());
            auditService.audit("DISBURSEMENT", "DISBURSEMENT_COMPLETED", "SUCCESS", disb.getId(), actor, "UNKNOWN", null, null, "Disbursement succeeded");
            disbursementRepository.save(disb);
        } else if (result.isTimeout()) {
            disb.setStatus(DisbursementStatus.SENT);
            auditService.audit("DISBURSEMENT", "DISBURSEMENT_SENT", "PENDING", disb.getId(), actor, "UNKNOWN", null, null, "Disbursement sent, awaiting callback");
            disbursementRepository.save(disb);
        } else {
            ledgerService.reverse(ledgerTxId, "Disbursement failure");
            disb.setStatus(DisbursementStatus.FAILED);
            auditService.audit("DISBURSEMENT", "DISBURSEMENT_FAILED", "FAILURE", disb.getId(), actor, "UNKNOWN", null, null, "Disbursement failed, reversal executed");
            disbursementRepository.save(disb);
        }

        return new DisbursementResponse(disb.getId(), source.getId(), msisdn, req.getAmount(), req.getRemarks(), disb.getStatus().name(), disb.getReceipt());
    }

    private GatewayResult invokeGatewayWithRetries(DisbursementCreateRequest req) {
        int attempts = 0;
        int maxAttempts = 2;
        long backoffMs = 100;
        while (attempts < maxAttempts) {
            try {
                return mobileMoneyGateway.send(req);
            } catch (TimeoutException te) {
                attempts++;
                if (attempts >= maxAttempts) {
                    return GatewayResult.timeout();
                }
                try { Thread.sleep(backoffMs * attempts); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                return GatewayResult.failure(e.getMessage());
            }
        }
        return GatewayResult.failure("Unexpected");
    }

    @Transactional(readOnly = true)
    public DisbursementResponse getDisbursement(UUID id) {
        Disbursement d = disbursementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Disbursement not found"));
        return new DisbursementResponse(d.getId(), d.getFromAccount().getId(), d.getMsisdn(), d.getAmount(), d.getRemarks(), d.getStatus().name(), d.getReceipt());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Auditable(action = "REVERSE_DISBURSEMENT", entityType = "Disbursement")
    public UUID reverseDisbursement(UUID disbursementId) {
        Disbursement d = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new IllegalArgumentException("Disbursement not found"));
        if (d.getStatus() == DisbursementStatus.FAILED) {
            throw new IllegalArgumentException("Disbursement already failed or reversed");
        }
        UUID reversalTxId = ledgerService.reverse(d.getLedgerTransactionId(), "Manual disbursement reversal");
        d.setStatus(DisbursementStatus.FAILED);
        disbursementRepository.save(d);
        auditService.audit("DISBURSEMENT", "DISBURSEMENT_MANUAL_REVERSAL", "SUCCESS", d.getId(), null, "ADMIN", null, null, "Manual reversal executed");
        return reversalTxId;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void handleCallback(DisbursementCallbackRequest callback) {
        Disbursement d = disbursementRepository.findById(callback.getDisbursementId())
                .orElseThrow(() -> new IllegalArgumentException("Disbursement not found"));
        if (d.getStatus() == DisbursementStatus.CONFIRMED || d.getStatus() == DisbursementStatus.FAILED) {
            return;
        }
        if (callback.isSuccessful()) {
            d.setStatus(DisbursementStatus.CONFIRMED);
            d.setReceipt(callback.getReceipt() != null ? callback.getReceipt() : "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            auditService.audit("DISBURSEMENT", "DISBURSEMENT_CALLBACK_SUCCESS", "SUCCESS", d.getId(), null, "MPESA_GATEWAY", null, null, "Callback succeeded");
            disbursementRepository.save(d);
        } else {
            ledgerService.reverse(d.getLedgerTransactionId(), "Disbursement callback failure");
            d.setStatus(DisbursementStatus.FAILED);
            auditService.audit("DISBURSEMENT", "DISBURSEMENT_CALLBACK_FAILURE", "FAILURE", d.getId(), null, "MPESA_GATEWAY", null, null, "Callback indicated failure");
            disbursementRepository.save(d);
        }
    }
}
