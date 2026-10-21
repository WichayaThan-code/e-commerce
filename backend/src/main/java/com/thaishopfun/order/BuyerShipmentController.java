package com.thaishopfun.order;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Buyer view of the shipment + timeline of the buyer's own orders (others answer 404). Staff user ids are hidden. */
@RestController
@RequestMapping("/api/orders")
public class BuyerShipmentController {
    private final ShipmentService shipments;

    public BuyerShipmentController(ShipmentService shipments) {
        this.shipments = shipments;
    }

    @RequirePermission("BUYER_SHIPMENT_READ")
    @GetMapping("/{orderId}/shipment")
    public ShipmentDtos.ShipmentDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return shipments.get(OrderActor.buyer(RequestUser.id(jwt)), orderId);
    }

    /** Tracking lookup by carrier + tracking number — only matches the caller's own orders (no public lookup). */
    @RequirePermission("BUYER_SHIPMENT_READ")
    @GetMapping("/tracking")
    public ShipmentDtos.ShipmentDetail track(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String carrierCode,
                                             @RequestParam(required = false) String trackingNumber) {
        return shipments.lookup(OrderActor.buyer(RequestUser.id(jwt)), carrierCode, trackingNumber);
    }
}
