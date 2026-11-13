package com.thaishopfun.order;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Shop shipment management. {shopId} = public shop number; other shops' orders answer 404, orders not accepted yet
 * 409 ORDER_NOT_ACCEPTED. Status changes sync the order through the order state machine (see ShipmentService).
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}")
public class MerchantShipmentController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final OrderQueryService queries;
    private final ShipmentService shipments;
    private final CarrierService carriers;
    private final ShipmentExportService exports;

    public MerchantShipmentController(OrderQueryService queries, ShipmentService shipments, CarrierService carriers,
                                      ShipmentExportService exports) {
        this.queries = queries;
        this.shipments = shipments;
        this.carriers = carriers;
        this.exports = exports;
    }

    @RequireShopPermission("SHIPMENT_READ")
    @GetMapping("/shipments")
    public OrderDtos.PageResponse<ShipmentDtos.ShipmentSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                                     @RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String carrierCode,
                                                                     @RequestParam(required = false) String trackingNumber,
                                                                     @RequestParam(required = false) String from,
                                                                     @RequestParam(required = false) String to,
                                                                     @RequestParam(required = false) Integer page,
                                                                     @RequestParam(required = false) Integer size) {
        return shipments.list(actor(jwt, shopId), null, new ShipmentService.Filters(status, carrierCode, trackingNumber, from, to, page, size));
    }

    /** Same filters as list; single data sheet (no Meta); all matching rows up to the export cap. */
    @RequireShopPermission("SHIPMENT_READ")
    @GetMapping(value = "/shipments/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String carrierCode,
                                         @RequestParam(required = false) String trackingNumber,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(required = false) String lang,
                                         Locale locale) {
        var filters = new ShipmentService.Filters(status, carrierCode, trackingNumber, from, to, 0, OrderQueryService.MAX_PAGE_SIZE);
        Locale loc = lang != null && !lang.isBlank() ? Locale.forLanguageTag(lang.trim()) : locale;
        byte[] body = exports.export(actor(jwt, shopId), shopId, filters, loc);
        String filename = ShipmentExportService.suggestedFilename(shopId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(XLSX)
            .body(body);
    }

    @RequireShopPermission("SHIPMENT_READ")
    @GetMapping("/orders/{orderId}/shipment")
    public ShipmentDtos.ShipmentDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return shipments.get(actor(jwt, shopId), orderId);
    }

    /** Ship the order: carrierCode + trackingNumber (defaults: tracking set earlier / shipping method carrier), shippedAt, note. */
    @RequireShopPermission("SHIPMENT_DISPATCH")
    @PostMapping("/orders/{orderId}/shipment")
    public ShipmentDtos.ActionResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                              @RequestBody(required = false) ShipmentDtos.CreateShipmentRequest request) {
        return shipments.ship(actor(jwt, shopId), orderId, request);
    }

    @RequireShopPermission("SHIPMENT_UPDATE")
    @PutMapping("/orders/{orderId}/shipment/tracking")
    public ShipmentDtos.ActionResponse tracking(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                @RequestBody(required = false) ShipmentDtos.TrackingRequest request) {
        return shipments.updateTracking(actor(jwt, shopId), orderId, request);
    }

    /** Timeline entry (note / location / carrierStatusCode), optionally with a status change (body.status). */
    @RequireShopPermission("SHIPMENT_UPDATE")
    @PostMapping("/orders/{orderId}/shipment/events")
    public ShipmentDtos.ActionResponse event(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                             @RequestBody(required = false) ShipmentDtos.ShipmentUpdateRequest request) {
        return shipments.update(actor(jwt, shopId), orderId, null, request);
    }

    @RequireShopPermission("SHIPMENT_UPDATE")
    @PostMapping("/orders/{orderId}/shipment/in-transit")
    public ShipmentDtos.ActionResponse inTransit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                 @RequestBody(required = false) ShipmentDtos.ShipmentUpdateRequest request) {
        return shipments.update(actor(jwt, shopId), orderId, ShipmentStatus.IN_TRANSIT, request);
    }

    @RequireShopPermission("SHIPMENT_UPDATE")
    @PostMapping("/orders/{orderId}/shipment/out-for-delivery")
    public ShipmentDtos.ActionResponse outForDelivery(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                      @RequestBody(required = false) ShipmentDtos.ShipmentUpdateRequest request) {
        return shipments.update(actor(jwt, shopId), orderId, ShipmentStatus.OUT_FOR_DELIVERY, request);
    }

    /** Shipment DELIVERED → order DELIVERED (same as POST …/orders/{orderId}/deliver). */
    @RequireShopPermission("SHIPMENT_UPDATE")
    @PostMapping("/orders/{orderId}/shipment/delivered")
    public ShipmentDtos.ActionResponse delivered(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                 @RequestBody(required = false) ShipmentDtos.ShipmentUpdateRequest request) {
        return shipments.update(actor(jwt, shopId), orderId, ShipmentStatus.DELIVERED, request);
    }

    /** DELIVERY_FAILED (reason required). returned=true: parcel coming back (→ RETURNED in the same call). */
    @RequireShopPermission("SHIPMENT_UPDATE")
    @PostMapping("/orders/{orderId}/shipment/failed")
    public ShipmentDtos.ActionResponse failed(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                              @RequestBody(required = false) ShipmentDtos.ShipmentUpdateRequest request) {
        return shipments.update(actor(jwt, shopId), orderId, ShipmentStatus.DELIVERY_FAILED, request);
    }

    /** RETURNED (reason required): COD not collected → order CANCELLED (restock once); prepaid → refund flow. */
    @RequireShopPermission("SHIPMENT_UPDATE")
    @PostMapping("/orders/{orderId}/shipment/returned")
    public ShipmentDtos.ActionResponse returned(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                @RequestBody(required = false) ShipmentDtos.ShipmentUpdateRequest request) {
        return shipments.update(actor(jwt, shopId), orderId, ShipmentStatus.RETURNED, request);
    }

    @RequireShopPermission("SHOP_READ")
    @GetMapping("/carriers")
    public ShipmentDtos.ShopCarriersResponse carriers(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        return carriers.forShop(queries.requireShop(shopId).getUuid());
    }

    @RequireShopPermission("SHOP_UPDATE")
    @PutMapping("/carriers/{carrierCode}")
    public ShipmentDtos.ShopCarriersResponse setCarrier(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable String carrierCode,
                                                        @RequestBody(required = false) ShipmentDtos.ShopCarrierRequest request) {
        return carriers.setEnabled(queries.requireShop(shopId).getUuid(), carrierCode, request == null ? null : request.enabled(), RequestUser.id(jwt));
    }

    private OrderActor actor(Jwt jwt, Long shopId) {
        return OrderActor.merchant(RequestUser.id(jwt), queries.requireShop(shopId).getUuid());
    }
}
