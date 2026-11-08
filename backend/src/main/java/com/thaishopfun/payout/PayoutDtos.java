package com.thaishopfun.payout;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PayoutDtos {
    private PayoutDtos() {}

    public record WalletBalance(
        BigDecimal available,
        BigDecimal reserved,
        BigDecimal pendingWithdrawals,
        String currency,
        String holdingPolicy,
        boolean canRequestWithdrawal
    ) {}

    public record LedgerEntryView(
        long id,
        String entryType,
        String reasonCode,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAvailableAfter,
        BigDecimal balanceReservedAfter,
        String sourceType,
        String sourceId,
        Instant createdAt
    ) {}

    public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record WithdrawalView(
        UUID id,
        long shopId,
        String shopName,
        BigDecimal amount,
        String currency,
        String status,
        String accountProvider,
        String accountBankName,
        String accountName,
        String accountNumberMasked,
        String accountPromptPayMasked,
        Instant requestedAt,
        Instant reviewedAt,
        String rejectReason,
        Instant paidAt,
        String transferReference,
        BigDecimal transferAmount,
        LocalDate transferDate,
        Instant cancelledAt,
        List<WithdrawalEventView> events
    ) {}

    public record WithdrawalEventView(
        String fromStatus,
        String toStatus,
        String eventCode,
        Instant createdAt,
        String note
    ) {}

    public record CreateWithdrawalRequest(
        BigDecimal amount,
        String currency,
        String accountProvider,
        String idempotencyKey
    ) {}

    public record RejectRequest(String reason) {}

    public record MarkPaidRequest(
        String transferReference,
        BigDecimal transferAmount,
        LocalDate transferDate,
        String externalTransferKey,
        String note
    ) {}

    public record NeedsReviewRequest(String reason) {}

    public record CreditRequest(
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        String note,
        String sourceReference
    ) {}
}
