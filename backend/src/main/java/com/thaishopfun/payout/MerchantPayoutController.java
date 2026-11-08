package com.thaishopfun.payout;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant/shops/{shopId}/finance")
public class MerchantPayoutController {
    private final OrderQueryService queries;
    private final ShopWalletService wallets;
    private final WithdrawalService withdrawals;
    private final PayoutCsvWriter csv;

    public MerchantPayoutController(
        OrderQueryService queries, ShopWalletService wallets, WithdrawalService withdrawals, PayoutCsvWriter csv
    ) {
        this.queries = queries;
        this.wallets = wallets;
        this.withdrawals = withdrawals;
        this.csv = csv;
    }

    @RequireShopPermission("FINANCE_READ")
    @GetMapping("/wallet")
    public PayoutDtos.WalletBalance wallet(@PathVariable Long shopId) {
        return wallets.balance(queries.requireShop(shopId).getUuid());
    }

    @RequireShopPermission("FINANCE_READ")
    @GetMapping("/ledger")
    public PayoutDtos.Page<PayoutDtos.LedgerEntryView> ledger(
        @PathVariable Long shopId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return wallets.ledger(queries.requireShop(shopId).getUuid(), page, size);
    }

    @RequireShopPermission("FINANCE_READ")
    @GetMapping("/withdrawals")
    public PayoutDtos.Page<PayoutDtos.WithdrawalView> list(
        @PathVariable Long shopId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "requestedAt") String dateField,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var shop = queries.requireShop(shopId);
        boolean paidAt = "paidAt".equalsIgnoreCase(dateField) || "transferDate".equalsIgnoreCase(dateField);
        return withdrawals.listForShop(shop.getUuid(), shop.getId(), shop.getName(), status, from, to, paidAt, page, size, false);
    }

    @RequireShopPermission("FINANCE_EXPORT")
    @GetMapping("/withdrawals/export.csv")
    public ResponseEntity<byte[]> exportCsv(
        @PathVariable Long shopId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "requestedAt") String dateField
    ) {
        var shop = queries.requireShop(shopId);
        boolean paidAt = "paidAt".equalsIgnoreCase(dateField) || "transferDate".equalsIgnoreCase(dateField);
        var page = withdrawals.listForShop(shop.getUuid(), shop.getId(), shop.getName(), status, from, to, paidAt, 0, 5000, false);
        var bytes = csv.write(page.items()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"withdrawals.csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(bytes);
    }

    @RequireShopPermission("FINANCE_READ")
    @GetMapping("/withdrawals/{withdrawalId}")
    public PayoutDtos.WithdrawalView get(@PathVariable Long shopId, @PathVariable UUID withdrawalId) {
        return withdrawals.get(withdrawalId, queries.requireShop(shopId).getUuid());
    }

    @RequireShopPermission("FINANCE_WITHDRAW")
    @PostMapping("/withdrawals")
    public PayoutDtos.WithdrawalView create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestBody PayoutDtos.CreateWithdrawalRequest body
    ) {
        return withdrawals.request(queries.requireShop(shopId), body, RequestUser.id(jwt));
    }

    @RequireShopPermission("FINANCE_WITHDRAW")
    @PostMapping("/withdrawals/{withdrawalId}/cancel")
    public PayoutDtos.WithdrawalView cancel(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID withdrawalId
    ) {
        return withdrawals.cancel(queries.requireShop(shopId), withdrawalId, RequestUser.id(jwt));
    }
}
