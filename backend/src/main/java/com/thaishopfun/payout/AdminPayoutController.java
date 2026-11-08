package com.thaishopfun.payout;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payouts")
public class AdminPayoutController {
    private final WithdrawalService withdrawals;
    private final OrderQueryService queries;

    public AdminPayoutController(WithdrawalService withdrawals, OrderQueryService queries) {
        this.withdrawals = withdrawals;
        this.queries = queries;
    }

    @RequirePermission("PLATFORM_PAYOUT_READ")
    @GetMapping
    public PayoutDtos.Page<PayoutDtos.WithdrawalView> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long shopId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return withdrawals.listAdmin(status, shopId, page, size);
    }

    @RequirePermission("PLATFORM_PAYOUT_READ")
    @GetMapping("/{withdrawalId}")
    public PayoutDtos.WithdrawalView get(@PathVariable UUID withdrawalId) {
        return withdrawals.get(withdrawalId, null);
    }

    @RequirePermission("PLATFORM_PAYOUT_REVIEW")
    @PostMapping("/{withdrawalId}/approve")
    public PayoutDtos.WithdrawalView approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID withdrawalId) {
        return withdrawals.approve(withdrawalId, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_PAYOUT_REVIEW")
    @PostMapping("/{withdrawalId}/reject")
    public PayoutDtos.WithdrawalView reject(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID withdrawalId,
        @RequestBody PayoutDtos.RejectRequest body
    ) {
        return withdrawals.reject(withdrawalId, body == null ? null : body.reason(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_PAYOUT_SETTLE")
    @PostMapping("/{withdrawalId}/processing")
    public PayoutDtos.WithdrawalView processing(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID withdrawalId) {
        return withdrawals.markProcessing(withdrawalId, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_PAYOUT_SETTLE")
    @PostMapping("/{withdrawalId}/needs-review")
    public PayoutDtos.WithdrawalView needsReview(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID withdrawalId,
        @RequestBody PayoutDtos.NeedsReviewRequest body
    ) {
        return withdrawals.needsReview(withdrawalId, body == null ? null : body.reason(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_PAYOUT_SETTLE")
    @PostMapping("/{withdrawalId}/paid")
    public PayoutDtos.WithdrawalView paid(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID withdrawalId,
        @RequestBody PayoutDtos.MarkPaidRequest body
    ) {
        return withdrawals.markPaid(withdrawalId, body, RequestUser.id(jwt));
    }

    /** Manual platform-holding credit only — never from order GMV. */
    @RequirePermission("PLATFORM_PAYOUT_CREDIT")
    @PostMapping("/shops/{shopId}/credits")
    public PayoutDtos.WalletBalance credit(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestBody PayoutDtos.CreditRequest body
    ) {
        return withdrawals.credit(queries.requireShop(shopId), body, RequestUser.id(jwt));
    }
}
