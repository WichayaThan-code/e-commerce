package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminDirectoryDtos.*;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Platform role directory and custom-role management endpoints. */
@RestController
@RequestMapping("/api/admin/roles")
public class AdminRoleController {
    private final AdminRoleDirectoryService roles;
    private final AdminRoleManagementService management;

    public AdminRoleController(AdminRoleDirectoryService roles, AdminRoleManagementService management) {
        this.roles = roles;
        this.management = management;
    }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping
    public List<RoleSummary> list() { return roles.list(); }

    @RequirePermission("PLATFORM_AUDIT_READ")
    @GetMapping("/activity")
    public List<ActivityEvent> activity(@RequestParam(defaultValue = "10") int limit) { return roles.activity(limit); }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping("/{code}")
    public RoleDetail detail(@PathVariable String code) { return roles.detail(code); }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping("/permissions")
    public List<PermissionInfo> platformPermissions() { return roles.assignablePermissions(); }

    @RequirePermission("PLATFORM_ROLE_CREATE")
    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public RoleDetail create(@Valid @RequestBody AdminManagementDtos.RoleRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return roles.detail(management.create(request, RequestUser.id(jwt)));
    }

    @RequirePermission("PLATFORM_ROLE_UPDATE")
    @PutMapping("/{code}")
    public RoleDetail update(@PathVariable String code, @Valid @RequestBody AdminManagementDtos.RoleRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        management.update(code, request, RequestUser.id(jwt));
        return roles.detail(code);
    }

    @RequirePermission("PLATFORM_ROLE_DELETE")
    @DeleteMapping("/{code}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        management.delete(code, RequestUser.id(jwt));
    }
}
