package com.thaishopfun.admin;

import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 1: platform system status for admins with {@code PLATFORM_SYSTEM_READ}.
 * Does not replace {@code GET /api/health}.
 */
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemStatusController {
    private final AdminSystemStatusService status;

    public AdminSystemStatusController(AdminSystemStatusService status) {
        this.status = status;
    }

    @RequirePermission("PLATFORM_SYSTEM_READ")
    @GetMapping("/status")
    public AdminSystemStatusService.SystemStatus status() {
        return status.status();
    }
}
