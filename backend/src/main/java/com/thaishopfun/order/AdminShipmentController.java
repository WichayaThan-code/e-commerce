package com.thaishopfun.order;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Platform view / override of shipments. Every override needs a reason (audited in both timelines). */
@RestController
@RequestMapping("/api/admin")
public class AdminShipmentController {
    private final OrderQueryService queries;
    private final ShipmentService shipments;

    public AdminShipmentController(OrderQueryService queries, ShipmentService shipments) {
        this.queries = queries;
        this.shipments = shipments;
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/shipments")
    public OrderDtos.PageResponse<ShipmentDtos.ShipmentSummary> list(@AuthenticationPrincipal Jwt jwt,
                                                                     @RequestParam(required = false) Long shopId,
                                                                     @RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String carrierCode,
                                                                     @RequestParam(required = false) String trackingNumber,
                                                                     @RequestParam(required = false) String from,
                                                                     @RequestParam(required = false) String to,
                                                                     @RequestParam(required = false) Integer page,
                                                                     @RequestParam(required = false) Integer size) {
        var shop = shopId == null ? null : queries.requireShop(shopId).getUuid();
        return shipments.list(OrderActor.admin(RequestUser.id(jwt)), shop,
            new ShipmentService.Filters(status, carrierCode, trackingNumber, from, to, page, size));
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/orders/{orderId}/shipment")
    public ShipmentDtos.ShipmentDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return shipments.get(OrderActor.admin(RequestUser.id(jwt)), orderId);
    }

    /** body.status (IN_TRANSIT | OUT_FOR_DELIVERY | DELIVERED | DELIVERY_FAILED | RETURNED) or a note; body.reason required. */
    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/orders/{orderId}/shipment/status")
    public ShipmentDtos.ActionResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                              @RequestBody(required = false) ShipmentDtos.ShipmentUpdateRequest request) {
        return shipments.update(OrderActor.admin(RequestUser.id(jwt)), orderId, null, request);
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PutMapping("/orders/{orderId}/shipment/tracking")
    public ShipmentDtos.ActionResponse tracking(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                                @RequestBody(required = false) ShipmentDtos.TrackingRequest request) {
        return shipments.updateTracking(OrderActor.admin(RequestUser.id(jwt)), orderId, request);
    }
}
