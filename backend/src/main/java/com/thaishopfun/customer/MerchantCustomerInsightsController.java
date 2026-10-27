package com.thaishopfun.customer;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Customer insights for the merchant customers page (accepted orders + CRM), separate from CRUD /customers.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/customer-insights")
public class MerchantCustomerInsightsController {
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CustomerInsightsService insights;

    public MerchantCustomerInsightsController(CustomerInsightsService insights) {
        this.insights = insights;
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping
    public CustomerInsightsDtos.InsightsPage list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String channel,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String segment,
                                                  @RequestParam(required = false) String joinedFrom,
                                                  @RequestParam(required = false) String joinedTo,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(required = false) String direction,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        return insights.list(RequestUser.id(jwt), shopId, q, channel, status, segment, joinedFrom, joinedTo, sort, direction, page, size);
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping("/channels")
    public List<String> channels(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        return insights.channels(RequestUser.id(jwt), shopId);
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String channel,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String segment,
                                         @RequestParam(required = false) String joinedFrom,
                                         @RequestParam(required = false) String joinedTo,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(required = false) String direction,
                                         @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        var body = insights.exportWorkbook(RequestUser.id(jwt), shopId, q, channel, status, segment, joinedFrom, joinedTo, sort, direction,
            localeOf(acceptLanguage));
        String filename = "customers-" + shopId + "-" + LocalDate.now(BANGKOK) + ".xlsx";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build().toString())
            .contentType(XLSX)
            .body(body);
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping("/{key}")
    public CustomerInsightsDtos.InsightDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable String key) {
        return insights.get(RequestUser.id(jwt), shopId, key);
    }

    @RequireShopPermission("CUSTOMER_READ")
    @GetMapping("/{key}/orders")
    public CustomerInsightsDtos.InsightOrdersPage orders(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable String key,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return insights.orders(RequestUser.id(jwt), shopId, key, page, size);
    }

    private static Locale localeOf(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return Locale.forLanguageTag("th");
        String primary = acceptLanguage.split(",")[0].trim();
        String lang = primary.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (lang.startsWith("en")) return Locale.ENGLISH;
        if (lang.startsWith("zh")) return Locale.SIMPLIFIED_CHINESE;
        return Locale.forLanguageTag("th");
    }
}
