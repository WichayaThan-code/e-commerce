package com.thaishopfun.customer;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.catalog.CatalogDtos;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Shop customer records (CRM/OMS). Permissions come from the DB matrix: CUSTOMER_READ (masked view),
 * CUSTOMER_PII_READ (full name/phone/email/addresses/notes, checked in the service), CUSTOMER_CREATE, CUSTOMER_UPDATE
 * (also used for archive and merge: the seed has no CUSTOMER_ARCHIVE / CUSTOMER_MERGE codes), ORDER_READ for history.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/customers")
public class MerchantCustomerController {
    private final CustomerService customers;

    public MerchantCustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping
    public CatalogDtos.PageResponse<CustomerDtos.CustomerResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                                        @RequestParam(required = false) String q,
                                                                        @RequestParam(required = false) String status,
                                                                        @RequestParam(required = false) String source,
                                                                        @RequestParam(required = false) String tag,
                                                                        @RequestParam(required = false) String sort,
                                                                        @RequestParam(required = false) Integer page,
                                                                        @RequestParam(required = false) Integer size) {
        return customers.list(RequestUser.id(jwt), shopId, q, status, source, tag, sort, page, size);
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping("/duplicates")
    public CustomerDtos.DuplicateCheckResponse duplicates(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                          @RequestParam(required = false) String phone,
                                                          @RequestParam(required = false) String email,
                                                          @RequestParam(required = false) UUID excludeId) {
        return customers.duplicates(RequestUser.id(jwt), shopId, phone, email, excludeId);
    }

    @RequireShopPermission("CUSTOMER_UPDATE")
    @PostMapping("/merge")
    public CustomerDtos.MergeResponse merge(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @RequestBody CustomerDtos.MergeRequest request) {
        return customers.merge(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("CUSTOMER_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDtos.CustomerResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @RequestBody CustomerDtos.CustomerWriteRequest request) {
        return customers.create(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping("/{customerId}")
    public CustomerDtos.CustomerResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID customerId) {
        return customers.get(RequestUser.id(jwt), shopId, customerId);
    }

    @RequireShopPermission("CUSTOMER_UPDATE")
    @PutMapping("/{customerId}")
    public CustomerDtos.CustomerResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID customerId,
                                                @RequestBody CustomerDtos.CustomerWriteRequest request) {
        return customers.update(RequestUser.id(jwt), shopId, customerId, request);
    }

    @RequireShopPermission("CUSTOMER_UPDATE")
    @DeleteMapping("/{customerId}")
    public CustomerDtos.CustomerResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID customerId) {
        return customers.archive(RequestUser.id(jwt), shopId, customerId);
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping("/{customerId}/duplicates")
    public CustomerDtos.DuplicateCheckResponse duplicatesOf(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID customerId) {
        return customers.duplicatesOf(RequestUser.id(jwt), shopId, customerId);
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/{customerId}/orders")
    public CustomerDtos.OrderHistoryResponse orders(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID customerId,
                                                    @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return customers.orders(RequestUser.id(jwt), shopId, customerId, page, size);
    }

    @RequireShopPermission("CUSTOMER_UPDATE")
    @PostMapping("/{customerId}/addresses")
    public CustomerDtos.CustomerResponse addAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID customerId,
                                                    @RequestBody CustomerDtos.AddressRequest request) {
        return customers.addAddress(RequestUser.id(jwt), shopId, customerId, request);
    }

    @RequireShopPermission("CUSTOMER_UPDATE")
    @PutMapping("/{customerId}/addresses/{addressId}")
    public CustomerDtos.CustomerResponse updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID customerId,
                                                       @PathVariable UUID addressId, @RequestBody CustomerDtos.AddressRequest request) {
        return customers.updateAddress(RequestUser.id(jwt), shopId, customerId, addressId, request);
    }

    @RequireShopPermission("CUSTOMER_UPDATE")
    @DeleteMapping("/{customerId}/addresses/{addressId}")
    public CustomerDtos.CustomerResponse deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID customerId,
                                                       @PathVariable UUID addressId) {
        return customers.deleteAddress(RequestUser.id(jwt), shopId, customerId, addressId);
    }
}
