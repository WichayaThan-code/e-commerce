package com.thaishopfun.order;

import com.thaishopfun.auth.authz.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PublicEndpoint("public order tracking: order number + shipping phone / buyer e-mail; masked view, rate limited")
@RequestMapping("/api/public/orders")
public class PublicOrderLookupController {
    private final PublicOrderLookupService service;

    public PublicOrderLookupController(PublicOrderLookupService service) { this.service = service; }

    @PostMapping("/lookup")
    public PublicOrderDtos.PublicOrderStatusView lookup(@RequestBody(required = false) PublicOrderDtos.LookupRequest body, HttpServletRequest request) {
        return service.lookup(body, request.getRemoteAddr());
    }
}