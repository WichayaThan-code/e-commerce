package com.thaishopfun.payment.gateway;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** All {@link PaymentGateway} beans by provider code. */
@Component
public class PaymentGatewayRegistry {
    private final Map<String, PaymentGateway> byProvider;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.byProvider = gateways.stream().collect(Collectors.toUnmodifiableMap(g -> g.provider().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public Optional<PaymentGateway> find(String provider) {
        return provider == null ? Optional.empty() : Optional.ofNullable(byProvider.get(provider.toUpperCase(Locale.ROOT)));
    }

    /** Gateway whose webhook endpoint is open; empty → the endpoint answers 404. */
    public Optional<PaymentGateway> webhook(String provider) {
        return find(provider).filter(PaymentGateway::webhookEnabled);
    }
}
