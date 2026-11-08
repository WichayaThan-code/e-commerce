package com.thaishopfun.merchant.team;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchant/shops/{shopId}/team")
public class MerchantTeamController {
    private final OrderQueryService shops;
    private final ShopTeamService team;

    public MerchantTeamController(OrderQueryService shops, ShopTeamService team) {
        this.shops = shops;
        this.team = team;
    }

    @RequireShopPermission("USER_READ")
    @GetMapping("/roles")
    public ShopTeamDtos.RoleCatalog roles(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        return team.roleCatalog(shops.requireShop(shopId), RequestUser.id(jwt));
    }

    @RequireShopPermission("USER_READ")
    @GetMapping("/members")
    public ShopTeamDtos.MemberPage members(
        @PathVariable Long shopId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return team.listMembers(shops.requireShop(shopId), q, role, status, page, size);
    }

    @RequireShopPermission("USER_READ")
    @GetMapping("/members/{userId}")
    public ShopTeamDtos.MemberDetail member(@PathVariable Long shopId, @PathVariable UUID userId) {
        return team.memberDetail(shops.requireShop(shopId), userId);
    }

    @RequireShopPermission("USER_ROLE_UPDATE")
    @PatchMapping("/members/{userId}")
    public ShopTeamDtos.MemberSummary changeRole(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID userId,
        @RequestBody ShopTeamDtos.ChangeRoleRequest body
    ) {
        return team.changeRole(shops.requireShop(shopId), userId, body, RequestUser.id(jwt));
    }

    @RequireShopPermission("USER_REMOVE")
    @DeleteMapping("/members/{userId}")
    public void removeMember(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID userId,
        @RequestBody(required = false) ShopTeamDtos.RemoveMemberRequest body
    ) {
        team.removeMember(shops.requireShop(shopId), userId, body, RequestUser.id(jwt));
    }

    @RequireShopPermission("USER_READ")
    @GetMapping("/invitations")
    public ShopTeamDtos.InvitationPage invitations(
        @PathVariable Long shopId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return team.listInvitations(shops.requireShop(shopId), q, status, page, size);
    }

    @RequireShopPermission("USER_INVITE")
    @PostMapping("/invitations")
    public ShopTeamDtos.InvitationCreated createInvite(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestBody ShopTeamDtos.CreateInviteRequest body
    ) {
        return team.createInvite(shops.requireShop(shopId), body, RequestUser.id(jwt));
    }

    @RequireShopPermission("USER_INVITE")
    @PostMapping("/invitations/{invitationId}/resend")
    public ShopTeamDtos.InvitationCreated resend(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID invitationId
    ) {
        return team.resendInvite(shops.requireShop(shopId), invitationId, RequestUser.id(jwt));
    }

    @RequireShopPermission("USER_INVITE")
    @PostMapping("/invitations/{invitationId}/revoke")
    public void revoke(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID invitationId
    ) {
        team.revokeInvite(shops.requireShop(shopId), invitationId, RequestUser.id(jwt));
    }
}
