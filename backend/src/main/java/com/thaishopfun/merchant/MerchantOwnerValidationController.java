package com.thaishopfun.merchant;

import com.thaishopfun.auth.authz.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchant-applications")
public class MerchantOwnerValidationController {
    private final MerchantApplicationService service;

    public MerchantOwnerValidationController(MerchantApplicationService service) {
        this.service = service;
    }

    @RequirePermission("SHOP_APPLICATION_UPDATE")
    @PostMapping("/validate-owner")
    public ResponseEntity<Void> validateOwner(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MerchantDtos.OwnerRequest request) {
        service.validateOwner(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.noContent().build();
    }
}
