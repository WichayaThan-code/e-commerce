package com.thaishopfun.merchant.plan;

import com.thaishopfun.admin.AdminPlatformDtos.PlanLimits;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShopPlanPurchaseDtos {
    private ShopPlanPurchaseDtos() {}

    public record PurchaseCatalogResponse(
        List<PurchaseCatalogPlan> plans,
        TransferAccount transferAccount,
        String currency,
        Instant asOf
    ) {}

    public record PurchaseCatalogPlan(
        String code,
        String name,
        String description,
        int sortOrder,
        boolean recommended,
        PlanLimits limits,
        BigDecimal priceMonthly,
        BigDecimal priceYearly,
        BigDecimal yearlySavingsBaht,
        BigDecimal yearlySavingsPercent,
        BigDecimal yearlyAvgPerMonth
    ) {}

    public record TransferAccount(
        String displayName,
        String bankName,
        String accountName,
        String accountNumber,
        String promptpay
    ) {}

    public record CreatePurchaseRequest(String planCode, String billingCycle) {}

    public record RejectPurchaseRequest(String reason) {}

    public record PurchaseOrderView(
        UUID id,
        long shopId,
        String shopName,
        String planCode,
        String planName,
        String billingCycle,
        BigDecimal amount,
        String currency,
        String status,
        Instant startsAt,
        Instant endsAt,
        Instant estimatedStartsAt,
        Instant estimatedEndsAt,
        UUID paymentProofFileId,
        String rejectReason,
        Instant createdAt,
        Instant updatedAt,
        Instant reviewedAt,
        TransferAccount transferAccount
    ) {}

    public record PurchaseHistoryResponse(List<PurchaseOrderView> items, PurchaseOrderView openOrder) {}
}
