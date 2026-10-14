package com.thaishopfun.admin;

import com.thaishopfun.admin.AdminDirectoryDtos.*;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Admin user directory and account lifecycle operations. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserDirectoryService users;
    private final AdminUserManagementService management;

    public AdminUserController(AdminUserDirectoryService users, AdminUserManagementService management) {
        this.users = users;
        this.management = management;
    }

    @RequirePermission("PLATFORM_ACCOUNT_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetail create(@Valid @RequestBody AdminManagementDtos.CreateUserRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return management.create(request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping
    public UserPage list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String dir,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return users.list(new AdminUserDirectoryService.Filter(q, role, status, from, to), sort, dir, page, size);
    }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping("/stats")
    public UserStats stats() { return users.stats(); }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping("/filter-options")
    public UserFilterOptions filterOptions() { return users.filterOptions(); }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String dir
    ) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users.csv\"")
            .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
            .body(users.exportCsv(new AdminUserDirectoryService.Filter(q, role, status, from, to), sort, dir));
    }

    @RequirePermission("PLATFORM_ACCOUNT_READ")
    @GetMapping("/{id}")
    public UserDetail detail(@PathVariable UUID id) { return users.detail(id); }

    @RequirePermission("PLATFORM_ACCOUNT_UPDATE")
    @PutMapping("/{id}")
    public UserDetail update(@PathVariable UUID id, @Valid @RequestBody AdminManagementDtos.UpdateUserRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return management.update(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_ACCOUNT_ROLE_ASSIGN")
    @PutMapping("/{id}/role")
    public UserDetail assignRole(@PathVariable UUID id, @RequestBody AdminManagementDtos.AssignRoleRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return management.assignRole(id, request.roleCode(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_ACCOUNT_SUSPEND")
    @PostMapping("/{id}/suspend")
    public UserDetail suspend(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return management.setStatus(id, "SUSPENDED", RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_ACCOUNT_RESTORE")
    @PostMapping("/{id}/restore")
    public UserDetail restore(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return management.setStatus(id, "ACTIVE", RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_ACCOUNT_DELETE")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        management.delete(id, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_AUDIT_READ")
    @GetMapping("/{id}/history")
    public List<ActivityEvent> history(@PathVariable UUID id, @RequestParam(defaultValue = "50") int limit) {
        return users.history(id, limit);
    }
}
