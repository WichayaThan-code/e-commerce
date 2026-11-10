package com.thaishopfun.platform;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.platform.SupportContactDtos.PublicSupportContact;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
@PublicEndpoint("public platform contact for /help")
public class PlatformContactPublicController {
    private final SupportContactService contacts;

    public PlatformContactPublicController(SupportContactService contacts) {
        this.contacts = contacts;
    }

    @GetMapping("/contact")
    public PublicSupportContact contact() {
        return contacts.publicContact();
    }
}
