package com.thaishopfun.order;

import com.thaishopfun.auth.authz.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Carrier master list (active carriers) for checkout / shop settings / tracking forms. */
@RestController
@RequestMapping("/api/carriers")
public class CarrierController {
    private final CarrierService carriers;

    public CarrierController(CarrierService carriers) {
        this.carriers = carriers;
    }

    @PublicEndpoint("carrier master list — reference data, no user data")
    @GetMapping
    public List<ShipmentDtos.CarrierView> list() {
        return carriers.list(true);
    }
}
