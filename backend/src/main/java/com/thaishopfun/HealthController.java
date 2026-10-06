package com.thaishopfun;

import com.thaishopfun.auth.authz.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @PublicEndpoint("liveness probe")
    @GetMapping("/api/health")
    Map<String, String> health() { return Map.of("status", "ok"); }
}
