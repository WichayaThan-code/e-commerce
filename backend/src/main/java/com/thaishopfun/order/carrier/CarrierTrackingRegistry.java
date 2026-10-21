package com.thaishopfun.order.carrier;

import com.thaishopfun.order.CarrierService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** All {@link CarrierTrackingProvider} beans by carrier code (empty in production today). */
@Component
public class CarrierTrackingRegistry {
    private final Map<String, CarrierTrackingProvider> byCarrier;

    public CarrierTrackingRegistry(List<CarrierTrackingProvider> providers) {
        this.byCarrier = providers.stream().collect(Collectors.toUnmodifiableMap(p -> CarrierService.normalizeCode(p.carrierCode()), Function.identity()));
    }

    public Optional<CarrierTrackingProvider> find(String carrier) {
        var code = CarrierService.normalizeCode(carrier);
        return code == null ? Optional.empty() : Optional.ofNullable(byCarrier.get(code));
    }

    /** Provider whose webhook is open; empty → the endpoint answers 404. */
    public Optional<CarrierTrackingProvider> webhook(String carrier) {
        return find(carrier).filter(CarrierTrackingProvider::webhookEnabled);
    }
}
