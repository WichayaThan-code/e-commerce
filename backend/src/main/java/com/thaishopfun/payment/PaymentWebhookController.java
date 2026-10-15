package com.thaishopfun.payment;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.payment.gateway.PaymentWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Gateway callbacks. 404 unless a {@code PaymentGateway} bean for the provider has its webhook enabled (none in
 * production today: manual transfer has no webhook). Authenticity comes from the provider signature, not a session.
 */
@RestController
@RequestMapping("/api/payments/webhooks")
public class PaymentWebhookController {
    private final PaymentWebhookService webhooks;

    public PaymentWebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PublicEndpoint("payment gateway webhook — verified by provider signature")
    @PostMapping("/{provider}")
    public PaymentDtos.WebhookAck receive(@PathVariable String provider, @RequestBody(required = false) byte[] body, HttpServletRequest request) {
        var headers = new LinkedHashMap<String, String>();
        for (var name : Collections.list(request.getHeaderNames())) headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        return webhooks.handle(provider, headers, body);
    }
}
