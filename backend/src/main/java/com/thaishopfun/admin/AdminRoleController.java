package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminDirectoryDtos.*;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only roles &amp; permissions catalog. Editing role permissions is intentionally not offered: the mapping is
 * generated into db/seed/seed_reference_data.sql, and re-running the seed would overwrite edits made here.
 */
@RestController
@RequestMapping("/api/admin/roles")
public class AdminRoleController {
    private final AdminRoleDirectoryService roles;

    public AdminRoleController(AdminRoleDirectoryService roles) { this.roles = roles; }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping
    public List<RoleSummary> list() { return roles.list(); }

    @RequirePermission("PLATFORM_AUDIT_READ")
    @GetMapping("/activity")
    public List<ActivityEvent> activity(@RequestParam(defaultValue = "10") int limit) { return roles.activity(limit); }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping("/{code}")
    public RoleDetail detail(@PathVariable String code) { return roles.detail(code); }
}
