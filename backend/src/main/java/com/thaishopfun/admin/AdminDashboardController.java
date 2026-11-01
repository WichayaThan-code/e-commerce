package com.thaishopfun.admin;

import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B5: platform admin dashboard KPIs. Needs PLATFORM_SHOP_READ (same gate as other /api/admin shop reads).
 * Period: range=today|7d|30d|1y or from/to (inclusive Bangkok days, max 3 years); default today.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService dashboard;

    public AdminDashboardController(AdminDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/summary")
    public AdminDashboardService.Summary summary(@RequestParam(required = false) String range,
                                                 @RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return dashboard.summary(range, from, to);
    }
}
