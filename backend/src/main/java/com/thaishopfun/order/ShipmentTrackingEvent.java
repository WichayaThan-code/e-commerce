package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * V1 {@code shipment_tracking_events} (append-only shipment timeline). source = channel (MERCHANT / ADMIN / SYSTEM /
 * CARRIER_WEBHOOK …); V30 adds who (actor_type, actor_user_id), the kind of entry (event_type) and the previous status.
 * normalized_status = shipment status after the entry. description = note / reason.
 */
@Entity
@Table(name = "shipment_tracking_events", uniqueConstraints = @UniqueConstraint(name = "uq_shipment_tracking_events_shipment_id_external_event_id",
    columnNames = {"shipment_id", "external_event_id"}))
public class ShipmentTrackingEvent extends AuditedEntity {
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String TRACKING_UPDATED = "TRACKING_UPDATED";
    public static final String NOTE = "NOTE";
    public static final String CARRIER_UPDATE = "CARRIER_UPDATE";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "shipment_id", nullable = false) private UUID shipmentId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(nullable = false, length = 30) private String source;
    @Column(name = "event_type", length = 40) private String eventType;
    @Column(name = "from_status", length = 40) private String fromStatus;
    @Column(name = "normalized_status", nullable = false, length = 40) private String normalizedStatus;
    @Column(name = "carrier_status_code", length = 80) private String carrierStatusCode;
    @Column(length = 500) private String description;
    @Column(length = 255) private String location;
    @Column(name = "event_at", nullable = false) private Instant eventAt;
    @Column(name = "actor_type", length = 20) private String actorType;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "external_event_id", length = 120) private String externalEventId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "raw_payload_safe") private Map<String, Object> rawPayloadSafe;

    protected ShipmentTrackingEvent() {}

    public ShipmentTrackingEvent(Shipment shipment, String source, String eventType, String fromStatus, String normalizedStatus,
                                 OrderActorType actorType, UUID actorUserId, Instant eventAt) {
        this.shipmentId = shipment.getId();
        this.shopId = shipment.getShopId();
        this.source = source;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.normalizedStatus = normalizedStatus;
        this.actorType = actorType == null ? null : actorType.name();
        this.actorUserId = actorUserId;
        this.eventAt = eventAt;
    }

    public Long getId() { return id; }
    public UUID getShipmentId() { return shipmentId; }
    public UUID getShopId() { return shopId; }
    public String getSource() { return source; }
    public String getEventType() { return eventType; }
    public String getFromStatus() { return fromStatus; }
    public String getNormalizedStatus() { return normalizedStatus; }
    public String getCarrierStatusCode() { return carrierStatusCode; }
    public void setCarrierStatusCode(String carrierStatusCode) { this.carrierStatusCode = carrierStatusCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Instant getEventAt() { return eventAt; }
    public String getActorType() { return actorType; }
    public UUID getActorUserId() { return actorUserId; }
    public String getExternalEventId() { return externalEventId; }
    public void setExternalEventId(String externalEventId) { this.externalEventId = externalEventId; }
    public Map<String, Object> getRawPayloadSafe() { return rawPayloadSafe; }
    public void setRawPayloadSafe(Map<String, Object> rawPayloadSafe) { this.rawPayloadSafe = rawPayloadSafe; }
}
