package com.thaishopfun.support;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.order.Order;
import com.thaishopfun.order.Payment;
import com.thaishopfun.payment.gateway.PaymentGateway;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Test-only gateway (provider CUSTOM): HMAC-SHA256 hex of the raw body in header X-Fake-Signature. */
@Component
public class FakePaymentGateway implements PaymentGateway {
    public static final String SECRET = "fake-webhook-secret";
    public static final String HEADER = "x-fake-signature";

    public static String sign(String body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public String provider() { return "CUSTOM"; }

    @Override public boolean webhookEnabled() { return true; }

    @Override
    public PaymentIntent createPaymentIntent(Order order) {
        return new PaymentIntent(provider(), "fake_" + order.getId(), order.getGrandTotal(), order.getCurrency(), Map.of("type", "FAKE"));
    }

    @Override
    public WebhookEvent verifyWebhook(Map<String, String> headers, byte[] body) {
        var raw = new String(body, StandardCharsets.UTF_8);
        var given = headers.get(HEADER);
        if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), sign(raw).getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookSignatureException("bad signature");
        }
        var ctx = JsonPath.parse(raw);
        String orderId = ctx.read("$.orderId");
        var safe = new LinkedHashMap<String, Object>();
        safe.put("type", ctx.read("$.type"));
        safe.put("paymentId", ctx.read("$.paymentId"));
        return new WebhookEvent(ctx.read("$.id"), ctx.read("$.type"), orderId == null ? null : UUID.fromString(orderId),
            ctx.read("$.paymentId"), new BigDecimal(ctx.read("$.amount").toString()), safe);
    }

    @Override
    public PaymentStatusResult queryStatus(Payment payment) {
        return new PaymentStatusResult(payment.getStatus(), payment.getProviderPaymentId(), payment.getAmount());
    }
}
