package com.thaishopfun.merchant;

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

    @GetMapping public MerchantDtos.ApplicationResponse get(@AuthenticationPrincipal Jwt jwt){return service.get(userId(jwt));}
    @PutMapping("/shop") public MerchantDtos.ApplicationResponse shop(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MerchantDtos.ShopRequest request){return service.saveShop(userId(jwt),request);}
    @PutMapping("/owner") public MerchantDtos.ApplicationResponse owner(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MerchantDtos.OwnerRequest request){return service.saveOwner(userId(jwt),request);}
    @PostMapping("/owner/{ownerId}") public MerchantDtos.ApplicationResponse attachOwner(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID ownerId){return service.attachOwner(userId(jwt),ownerId);}
    @PostMapping(value="/documents/{type}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public MerchantDtos.ApplicationResponse document(@AuthenticationPrincipal Jwt jwt,@PathVariable DocumentType type,@RequestPart("file") MultipartFile file){return service.upload(userId(jwt),type,file);}
    @PostMapping("/submit") public MerchantDtos.ApplicationResponse submit(@AuthenticationPrincipal Jwt jwt){return service.submit(userId(jwt));}
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
