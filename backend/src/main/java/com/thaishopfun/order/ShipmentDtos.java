package com.thaishopfun.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request / response bodies of the shipment, tracking and carrier APIs. */
public final class ShipmentDtos {
    private ShipmentDtos() {}

    public record CarrierView(String code, String nameTh, String nameEn, String trackingPattern, boolean active, int sortOrder) {}

    /** restricted = the shop has chosen carriers (only enabled ones are accepted); false = every active carrier. */
    public record ShopCarriersResponse(boolean restricted, List<ShopCarrierView> carriers) {}

    public record ShopCarrierView(String code, String nameTh, String nameEn, String trackingPattern, boolean active, boolean enabled) {}

    public record ShopCarrierRequest(Boolean enabled) {}

    /** POST …/orders/{orderId}/shipment = ship the order (order → DISPATCHED). expectedOrderVersion = orders.row_version. */
    public record CreateShipmentRequest(String carrierCode, String trackingNumber, String shippedAt, String note, Integer expectedOrderVersion) {}

    /** PUT …/shipment/tracking. expectedVersion = shipments.row_version. reason required for admins. */
    public record TrackingRequest(String carrierCode, String trackingNumber, String reason, Integer expectedVersion) {}

    /**
     * Status change / timeline entry. status: IN_TRANSIT | OUT_FOR_DELIVERY | DELIVERED | DELIVERY_FAILED (alias FAILED)
     * | RETURNED. reason required for DELIVERY_FAILED / RETURNED (and always for admins). returned=true on
     * DELIVERY_FAILED = the parcel is coming back (FAILED then RETURNED in one call). eventAt: ISO-8601 (with zone) or
     * local Asia/Bangkok "yyyy-MM-ddTHH:mm[:ss]"; default now.
     */
    public record ShipmentUpdateRequest(String status, String reason, String note, String location, String eventAt,
                                        String carrierStatusCode, Boolean returned, Integer expectedVersion, Integer expectedOrderVersion) {}

    public record TrackingEventView(Long id, String eventType, String source, String actorType, UUID actorUserId, String fromStatus,
                                    String status, String carrierStatusCode, String note, String location, Instant eventAt, Instant recordedAt) {}

    public record ShipmentDetail(UUID id, UUID orderId, String orderNumber, String orderStatus, int orderVersion, String paymentMethod,
                                 String status, String carrierCode, String carrierName, String trackingNumber, String trackingUrl,
                                 Instant packedAt, Instant shippedAt, Instant pickedUpAt, Instant deliveredAt, Instant failedAt,
                                 String failureReason, Instant returnedAt, Instant lastTrackingEventAt, UUID packedBy, UUID shippedBy,
                                 int version, List<String> allowedStatuses, List<TrackingEventView> events) {}

    public record ShipmentSummary(UUID id, UUID orderId, String orderNumber, String orderStatus, String status, String carrierCode,
                                  String trackingNumber, Instant shippedAt, Instant deliveredAt, Instant lastTrackingEventAt, int version) {}

    public record ActionResponse(boolean changed, String fromStatus, String toStatus, String orderFromStatus, String orderToStatus,
                                 ShipmentDetail shipment) {}

    public record WebhookAck(boolean accepted, int received, int applied, int duplicates, int recorded, int unmatched, int failed) {}
}
