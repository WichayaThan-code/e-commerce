package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** V1 {@code shipments}: one shipment per order (no split shipments yet). V30: row_version, failed / returned facts. */
@Entity
@Table(name = "shipments")
public class Shipment extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "shipping_method_id") private UUID shippingMethodId;
    @Column(nullable = false, length = 40) private String status = "READY_TO_PACK";
    @Column(name = "carrier_code", length = 50) private String carrierCode;
    @Column(name = "tracking_number", length = 100) private String trackingNumber;
    @Column(name = "packed_at") private Instant packedAt;
    @Column(name = "dispatched_at") private Instant dispatchedAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "packed_by") private UUID packedBy;
    @Column(name = "dispatched_by") private UUID dispatchedBy;
    @Column(name = "oms_sync_status", nullable = false, length = 30) private String omsSyncStatus = "NOT_SYNCED";
    @Column(name = "picked_up_at") private Instant pickedUpAt;
    @Column(name = "last_tracking_event_at") private Instant lastTrackingEventAt;
    @Column(name = "failed_at") private Instant failedAt;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @Column(name = "returned_at") private Instant returnedAt;
    @Version @Column(name = "row_version", nullable = false) private int rowVersion;

    protected Shipment() {}

    public Shipment(UUID orderId, UUID shopId, UUID shippingMethodId) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.shopId = shopId;
        this.shippingMethodId = shippingMethodId;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getShopId() { return shopId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public Instant getPackedAt() { return packedAt; }
    public void setPackedAt(Instant packedAt, UUID by) { this.packedAt = packedAt; this.packedBy = by; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(Instant dispatchedAt, UUID by) { this.dispatchedAt = dispatchedAt; this.dispatchedBy = by; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public UUID getShippingMethodId() { return shippingMethodId; }
    public UUID getPackedBy() { return packedBy; }
    public UUID getDispatchedBy() { return dispatchedBy; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(Instant pickedUpAt) { this.pickedUpAt = pickedUpAt; }
    public Instant getLastTrackingEventAt() { return lastTrackingEventAt; }
    public void setLastTrackingEventAt(Instant lastTrackingEventAt) { this.lastTrackingEventAt = lastTrackingEventAt; }
    public Instant getFailedAt() { return failedAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailed(Instant failedAt, String reason) { this.failedAt = failedAt; this.failureReason = reason; }
    public Instant getReturnedAt() { return returnedAt; }
    public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
    public int getRowVersion() { return rowVersion; }
    public ShipmentStatus statusEnum() { return ShipmentStatus.parse(status); }
}
