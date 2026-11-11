package com.thaishopfun.support;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.order.CarrierService;
import com.thaishopfun.order.ShipmentService;
import com.thaishopfun.order.carrier.CarrierTrackingProvider;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only carrier integration for FLASH: HMAC-SHA256 hex of the raw body in header X-Fake-Carrier-Signature;
 * body {"events":[{"id","tracking","status","code","description","location","at"}]}.
 * {@link #poll(String)} reads statuses seeded via {@link #setPollStatus(String, String)}.
 */
@Component
public class FakeCarrierTrackingProvider implements CarrierTrackingProvider {
    public static final String SECRET = "fake-carrier-secret";
    public static final String HEADER = "x-fake-carrier-signature";

    private static final ConcurrentHashMap<String, String> POLL_STATUS = new ConcurrentHashMap<>();

    public static String sign(String body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setPollStatus(String trackingNumber, String status) {
        var tracking = CarrierService.normalizeTracking(trackingNumber);
        if (tracking == null) return;
        if (status == null || status.isBlank()) POLL_STATUS.remove(tracking);
        else POLL_STATUS.put(tracking, status.trim());
    }

    public static void clearPollStatuses() { POLL_STATUS.clear(); }

    @Override public String carrierCode() { return "FLASH"; }

    @Override public boolean webhookEnabled() { return true; }

    @Override public boolean supportsPoll() { return true; }

    @Override
    public List<ShipmentService.CarrierUpdate> poll(String trackingNumber) {
        var tracking = CarrierService.normalizeTracking(trackingNumber);
        if (tracking == null) return List.of();
        var status = POLL_STATUS.get(tracking);
        if (status == null) return List.of();
        var safe = new LinkedHashMap<String, Object>();
        safe.put("source", "poll");
        return List.of(new ShipmentService.CarrierUpdate("poll-" + tracking + "-" + status, tracking, status,
            status, "fake poll " + status, null, Instant.now(), safe));
    }

    @Override
    public List<ShipmentService.CarrierUpdate> verifyWebhook(Map<String, String> headers, byte[] body) {
        var raw = new String(body, StandardCharsets.UTF_8);
        var given = headers.get(HEADER);
        if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), sign(raw).getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookSignatureException("bad signature");
        }
        List<Map<String, Object>> events = JsonPath.read(raw, "$.events");
        var updates = new ArrayList<ShipmentService.CarrierUpdate>();
        for (var e : events) {
            var safe = new LinkedHashMap<String, Object>();
            safe.put("code", e.get("code"));
            var at = (String) e.get("at");
            updates.add(new ShipmentService.CarrierUpdate((String) e.get("id"), (String) e.get("tracking"), (String) e.get("status"),
                (String) e.get("code"), (String) e.get("description"), (String) e.get("location"), at == null ? null : Instant.parse(at), safe));
        }
        return updates;
    }
}
