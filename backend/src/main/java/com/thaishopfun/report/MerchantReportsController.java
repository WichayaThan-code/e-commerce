package com.thaishopfun.report;

import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.common.excel.MerchantExcelWorkbook;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Merchant reports for one shop. {shopId} = public shop number.
 * overview (all tabs) needs REPORT_READ; export.xlsx needs REPORT_EXPORT (viewers can read but not export).
 * Period params match the dashboard: range=today|7d|30d|1y or from+to (YYYY-MM-DD, Asia/Bangkok, inclusive, max 3 years).
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/reports")
public class MerchantReportsController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final OrderQueryService queries;
    private final MerchantReportsService reports;

    public MerchantReportsController(OrderQueryService queries, MerchantReportsService reports) {
        this.queries = queries;
        this.reports = reports;
    }

    @RequireShopPermission("REPORT_READ")
    @GetMapping("/overview")
    public MerchantReportsService.Overview overview(@PathVariable Long shopId,
                                                    @RequestParam(required = false) String range,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        return reports.overview(shopId, queries.requireShop(shopId).getUuid(), range, from, to);
    }

    /** tab = overview | sales | products | orders | customers | promotions | fees | shipping. Labels follow Accept-Language (or lang=th|en|zh). */
    @RequireShopPermission("REPORT_EXPORT")
    @GetMapping(value = "/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportXlsx(@PathVariable Long shopId,
                                             @RequestParam(required = false) String range,
                                             @RequestParam(required = false) String from,
                                             @RequestParam(required = false) String to,
                                             @RequestParam(required = false) String tab,
                                             @RequestParam(required = false) String lang,
                                             Locale locale) {
        return buildExport(shopId, range, from, to, tab, lang, locale);
    }

    /**
     * Legacy path kept so older clients do not 404 — still returns a real .xlsx (not CSV renamed).
     * Prefer {@code /export.xlsx}.
     */
    @RequireShopPermission("REPORT_EXPORT")
    @GetMapping(value = "/export.csv", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportLegacy(@PathVariable Long shopId,
                                               @RequestParam(required = false) String range,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) String tab,
                                               @RequestParam(required = false) String lang,
                                               Locale locale) {
        return buildExport(shopId, range, from, to, tab, lang, locale);
    }

    private ResponseEntity<byte[]> buildExport(Long shopId, String range, String from, String to, String tab, String lang, Locale locale) {
        String normalizedTab = ReportCsvWriter.normalizeTab(tab);
        var period = reports.period(range, from, to);
        var shop = queries.requireShop(shopId);
        var overview = reports.overview(shopId, shop.getUuid(), period);
        String language = lang != null && !lang.isBlank() ? lang.trim().toLowerCase() : locale == null ? "en" : locale.getLanguage();
        String shopLabel = shop.getName() != null ? shop.getName() : String.valueOf(shopId);
        byte[] body = ReportXlsxWriter.write(overview, normalizedTab, language, shopLabel);
        String filename = MerchantExcelWorkbook.filename(
            "thaishopfun-report-" + normalizedTab + "-" + period.from() + "_" + period.to(),
            LocalDate.now(MerchantExcelWorkbook.BANGKOK));
        return ResponseEntity.ok()
            .contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(body);
    }
}
