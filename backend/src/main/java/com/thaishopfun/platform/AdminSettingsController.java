package com.thaishopfun.platform;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.platform.SupportContactDtos.SupportContact;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {
    private final SupportContactService contacts;

    public AdminSettingsController(SupportContactService contacts) {
        this.contacts = contacts;
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @GetMapping("/contact")
    public SupportContact getContact() {
        return contacts.adminContact();
    }

    @RequirePermission("PLATFORM_SETTING_UPDATE")
    @PutMapping("/contact")
    public SupportContact putContact(@RequestBody SupportContact body, @AuthenticationPrincipal Jwt jwt) {
        return contacts.update(body, RequestUser.id(jwt));
    }
}
