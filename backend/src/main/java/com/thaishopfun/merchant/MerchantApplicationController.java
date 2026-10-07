package com.thaishopfun.merchant;

import com.thaishopfun.auth.authz.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchant-applications/draft")
public class MerchantApplicationController {
    private final MerchantApplicationService service;
    public MerchantApplicationController(MerchantApplicationService service){this.service=service;}

    @RequirePermission("SHOP_APPLICATION_READ")
    @GetMapping public MerchantDtos.ApplicationResponse get(@AuthenticationPrincipal Jwt jwt){return service.get(userId(jwt));}
    @RequirePermission("SHOP_APPLICATION_UPDATE")
    @PutMapping("/shop") public MerchantDtos.ApplicationResponse shop(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MerchantDtos.ShopRequest request){return service.saveShop(userId(jwt),request);}
    @RequirePermission("SHOP_APPLICATION_UPDATE")
    @PutMapping("/owner") public MerchantDtos.ApplicationResponse owner(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MerchantDtos.OwnerRequest request){return service.saveOwner(userId(jwt),request);}
    @RequirePermission("SHOP_APPLICATION_UPDATE")
    @PostMapping("/owner/{ownerId}") public MerchantDtos.ApplicationResponse attachOwner(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID ownerId){return service.attachOwner(userId(jwt),ownerId);}
    @RequirePermission("SHOP_APPLICATION_FILE_UPLOAD")
    @PostMapping(value="/documents/{type}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public MerchantDtos.ApplicationResponse document(@AuthenticationPrincipal Jwt jwt,@PathVariable DocumentType type,@RequestPart("file") MultipartFile file){return service.upload(userId(jwt),type,file);}
    @RequirePermission("SHOP_APPLICATION_SUBMIT")
    @PostMapping("/submit") public MerchantDtos.ApplicationResponse submit(@AuthenticationPrincipal Jwt jwt){return service.submit(userId(jwt));}
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
