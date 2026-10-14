package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminDirectoryDtos.ShopOption;
import com.thaishopfun.admin.AdminDirectoryDtos.ShopRoleMember;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/shop-role-assignments")
public class AdminShopRoleAssignmentController {
    private final AdminShopRoleAssignmentService assignments;

    public AdminShopRoleAssignmentController(AdminShopRoleAssignmentService assignments) {
        this.assignments = assignments;
    }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping("/shops")
    public List<ShopOption> shops() {
        return assignments.shops();
    }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping
    public List<ShopRoleMember> members(@RequestParam long shopId) {
        return assignments.members(shopId);
    }

    @RequirePermission("PLATFORM_ACCOUNT_ROLE_ASSIGN")
    @PutMapping("/{shopId}/{userId}")
    public ShopRoleMember assign(@PathVariable long shopId, @PathVariable UUID userId,
                                 @Valid @RequestBody AdminManagementDtos.ShopRoleAssignmentRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return assignments.assign(shopId, userId, request.roleCode(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_ACCOUNT_ROLE_ASSIGN")
    @DeleteMapping("/{shopId}/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long shopId, @PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        assignments.remove(shopId, userId, RequestUser.id(jwt));
    }
}
