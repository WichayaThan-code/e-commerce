package com.thaishopfun.order;

import org.springframework.stereotype.Component;

import com.thaishopfun.common.FieldViolation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Writes shipment_tracking_events (the shipment timeline) and keeps shipments.last_tracking_event_at current. */
@Component
public class ShipmentTimeline {
    /**
     * Extra facts for the entry. source null = from the actor (MERCHANT / ADMIN / SYSTEM / BUYER); eventAt null = now.
     */
    public record Detail(String source, String note, String location, Instant eventAt, String carrierStatusCode,
                         String externalEventId, Map<String, Object> safePayload) {
        public static Detail none() { return new Detail(null, null, null, null, null, null, null); }
    }

    private final ShipmentTrackingEventRepository events;

    public ShipmentTimeline(ShipmentTrackingEventRepository events) {
        this.events = events;
    }

    public ShipmentTrackingEvent record(Shipment shipment, String eventType, String fromStatus, OrderActor actor, String reason, Detail detail) {
        var d = detail == null ? Detail.none() : detail;
        var at = d.eventAt() == null ? Instant.now() : d.eventAt();
        var source = d.source() != null ? d.source() : switch (actor.type()) {
            case MERCHANT -> "MERCHANT";
            case ADMIN -> "ADMIN";
            case BUYER -> "BUYER";
            case SYSTEM -> "SYSTEM";
        };
        var event = new ShipmentTrackingEvent(shipment, source, eventType, fromStatus, shipment.getStatus(), actor.type(), actor.userId(), at);
        event.setDescription(text(reason, d.note()));
        event.setLocation(d.location());
        event.setCarrierStatusCode(d.carrierStatusCode());
        event.setExternalEventId(d.externalEventId());
        event.setRawPayloadSafe(d.safePayload() == null || d.safePayload().isEmpty() ? null : d.safePayload());
        event.stamp(actor.userId());
        if (shipment.getLastTrackingEventAt() == null || at.isAfter(shipment.getLastTrackingEventAt())) shipment.setLastTrackingEventAt(at);
        return events.save(event);
    }

    /** Status change entry when the status really moved (no-op otherwise). */
    public void statusChanged(Shipment shipment, String fromStatus, OrderActor actor, String reason, Detail detail) {
        if (fromStatus != null && fromStatus.equals(shipment.getStatus())) return;
        record(shipment, ShipmentTrackingEvent.STATUS_CHANGED, fromStatus, actor, reason, detail);
    }

    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    static final Duration CLOCK_SKEW = Duration.ofMinutes(10);

    /**
     * Event / shipped time from a request: ISO instant or offset date-time, or local Asia/Bangkok date-time. Not more
     * than 10 minutes in the future, not before notBefore (order placed). null / blank = null (caller uses now).
     */
    public static Instant parseTime(String raw, String field, Instant notBefore, List<FieldViolation> violations) {
        if (raw == null || raw.isBlank()) return null;
        var value = raw.trim();
        Instant at;
        try {
            at = OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException offset) {
            try {
                at = LocalDateTime.parse(value).atZone(BANGKOK).toInstant();
            } catch (DateTimeParseException local) {
                violations.add(new FieldViolation(field, "INVALID", field + " ต้องเป็นวันเวลา ISO-8601 เช่น 2026-10-19T14:30:00+07:00"));
                return null;
            }
        }
        if (at.isAfter(Instant.now().plus(CLOCK_SKEW))) {
            violations.add(new FieldViolation(field, "FUTURE", field + " ต้องไม่เป็นเวลาในอนาคต"));
            return null;
        }
        if (notBefore != null && at.isBefore(notBefore.minus(Duration.ofMinutes(1)))) {
            violations.add(new FieldViolation(field, "BEFORE_ORDER", field + " ต้องไม่ก่อนเวลาสั่งซื้อ"));
            return null;
        }
        return at;
    }

    private static String text(String reason, String note) {
        if (reason == null) return note;
        if (note == null || note.equals(reason)) return reason;
        var joined = reason + " | " + note;
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }
}
