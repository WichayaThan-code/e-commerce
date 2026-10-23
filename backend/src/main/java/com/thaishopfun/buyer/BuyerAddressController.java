package com.thaishopfun.buyer;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Signed-in buyer's address book (V1 buyer_addresses). Permissions seeded as BUYER_ADDRESS_READ / BUYER_ADDRESS_UPDATE. */
@RestController
@RequestMapping("/api/me/addresses")
public class BuyerAddressController {
    private final BuyerAddressService addresses;

    public BuyerAddressController(BuyerAddressService addresses) {
        this.addresses = addresses;
    }

    @RequirePermission("BUYER_ADDRESS_READ")
    @GetMapping
    public List<BuyerAddressDtos.AddressView> list(@AuthenticationPrincipal Jwt jwt) {
        return addresses.list(RequestUser.id(jwt));
    }

    @RequirePermission("BUYER_ADDRESS_UPDATE")
    @PostMapping
    public ResponseEntity<BuyerAddressDtos.AddressView> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) BuyerAddressDtos.AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addresses.create(RequestUser.id(jwt), request));
    }

    @RequirePermission("BUYER_ADDRESS_UPDATE")
    @PutMapping("/{id}")
    public BuyerAddressDtos.AddressView update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) BuyerAddressDtos.AddressRequest request) {
        return addresses.update(RequestUser.id(jwt), id, request);
    }

    @RequirePermission("BUYER_ADDRESS_UPDATE")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        addresses.delete(RequestUser.id(jwt), id);
    }
}
