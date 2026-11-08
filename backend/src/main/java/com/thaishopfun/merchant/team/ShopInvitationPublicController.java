package com.thaishopfun.merchant.team;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
public class ShopInvitationPublicController {
    private final ShopTeamService team;

    public ShopInvitationPublicController(ShopTeamService team) {
        this.team = team;
    }

    /** Preview only — never auto-accepts. Token is in the path; response never echoes the raw token. */
    @PublicEndpoint
    @GetMapping("/{token}")
    public ShopTeamDtos.InvitePreview preview(
        @PathVariable String token,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID caller = null;
        try {
            if (jwt != null) caller = RequestUser.id(jwt);
        } catch (RuntimeException ignored) {
            caller = null;
        }
        return team.previewInvite(token, caller);
    }

    /**
     * Accept requires a signed-in account (BUYER_PROFILE_READ is held by every active account).
     * Email match + verification are enforced in {@link ShopTeamService#acceptInvite}.
     */
    @RequirePermission("BUYER_PROFILE_READ")
    @PostMapping("/{token}/accept")
    public ShopTeamDtos.MemberSummary accept(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String token
    ) {
        return team.acceptInvite(token, RequestUser.id(jwt));
    }
}
