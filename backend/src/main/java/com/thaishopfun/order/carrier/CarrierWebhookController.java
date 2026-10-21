package com.thaishopfun.order.carrier;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.order.ShipmentDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Carrier tracking callbacks. 404 unless a {@link CarrierTrackingProvider} for the carrier has its webhook enabled. */
@RestController
@RequestMapping("/api/shipping/webhooks")
public class CarrierWebhookController {
    private final CarrierWebhookService webhooks;

    public CarrierWebhookController(CarrierWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PublicEndpoint("carrier tracking webhook — verified by carrier signature")
    @PostMapping("/{carrier}")
    public ShipmentDtos.WebhookAck receive(@PathVariable String carrier, @RequestBody(required = false) byte[] body, HttpServletRequest request) {
        var headers = new LinkedHashMap<String, String>();
        for (var name : Collections.list(request.getHeaderNames())) headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        return webhooks.handle(carrier, headers, body);
    }
}
