package com.thaishopfun.invoice;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant/shops/{shopId}/finance/invoices")
public class MerchantInvoiceController {
    private final OrderQueryService queries;
    private final InvoiceService invoices;
    private final ShopTaxProfileService taxProfiles;
    private final InvoicePdfWriter pdf;
    private final InvoiceCsvWriter csv;

    public MerchantInvoiceController(
        OrderQueryService queries, InvoiceService invoices, ShopTaxProfileService taxProfiles,
        InvoicePdfWriter pdf, InvoiceCsvWriter csv
    ) {
        this.queries = queries;
        this.invoices = invoices;
        this.taxProfiles = taxProfiles;
        this.pdf = pdf;
        this.csv = csv;
    }

    @RequireShopPermission("FINANCE_READ")
    @GetMapping("/tax-profile")
    public InvoiceDtos.TaxProfileView taxProfile(@PathVariable Long shopId) {
        return taxProfiles.view(queries.requireShop(shopId).getUuid());
    }

    @RequireShopPermission("FINANCE_INVOICE_MANAGE")
    @PutMapping("/tax-profile")
    public InvoiceDtos.TaxProfileView upsertTaxProfile(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestBody InvoiceDtos.UpsertTaxProfileRequest body
    ) {
        return taxProfiles.upsert(queries.requireShop(shopId).getUuid(), body, RequestUser.id(jwt));
    }

    @RequireShopPermission("FINANCE_READ")
    @GetMapping
    public InvoiceDtos.Page<InvoiceDtos.InvoiceSummary> list(
        @PathVariable Long shopId,
        @RequestParam(required = false) String docType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "createdAt") String dateField,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var shop = queries.requireShop(shopId);
        return invoices.list(shop, new InvoiceDtos.ListFilter(docType, status, q, from, to, dateField, page, size));
    }

    @RequireShopPermission("FINANCE_EXPORT")
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
        @PathVariable Long shopId,
        @RequestParam(required = false) String docType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "createdAt") String dateField
    ) {
        var shop = queries.requireShop(shopId);
        var page = invoices.list(shop, new InvoiceDtos.ListFilter(docType, status, q, from, to, dateField, 0, 5000));
        var bytes = csv.write(page.items()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(bytes);
    }

    @RequireShopPermission("FINANCE_READ")
    @GetMapping("/{invoiceId}")
    public InvoiceDtos.InvoiceDetail get(@PathVariable Long shopId, @PathVariable UUID invoiceId) {
        return invoices.get(queries.requireShop(shopId), invoiceId);
    }

    @RequireShopPermission("FINANCE_READ")
    @GetMapping("/{invoiceId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long shopId, @PathVariable UUID invoiceId) {
        var detail = invoices.get(queries.requireShop(shopId), invoiceId);
        var bytes = pdf.write(detail);
        var name = detail.documentNumber() == null ? detail.id().toString() : detail.documentNumber();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(bytes);
    }

    @RequireShopPermission("FINANCE_INVOICE_MANAGE")
    @PostMapping
    public InvoiceDtos.InvoiceDetail create(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @RequestBody InvoiceDtos.CreateFromOrderRequest body
    ) {
        return invoices.createFromOrder(queries.requireShop(shopId), body, RequestUser.id(jwt));
    }

    @RequireShopPermission("FINANCE_INVOICE_MANAGE")
    @PatchMapping("/{invoiceId}")
    public InvoiceDtos.InvoiceDetail updateDraft(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID invoiceId,
        @RequestBody InvoiceDtos.UpdateDraftRequest body
    ) {
        return invoices.updateDraft(queries.requireShop(shopId), invoiceId, body, RequestUser.id(jwt));
    }

    @RequireShopPermission("FINANCE_INVOICE_MANAGE")
    @PostMapping("/{invoiceId}/issue")
    public InvoiceDtos.InvoiceDetail issue(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID invoiceId,
        @RequestBody(required = false) InvoiceDtos.IssueRequest body
    ) {
        return invoices.issue(queries.requireShop(shopId), invoiceId, body, RequestUser.id(jwt));
    }

    @RequireShopPermission("FINANCE_INVOICE_MANAGE")
    @PostMapping("/{invoiceId}/cancel")
    public InvoiceDtos.InvoiceDetail cancel(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long shopId,
        @PathVariable UUID invoiceId,
        @RequestBody InvoiceDtos.CancelRequest body
    ) {
        return invoices.cancel(queries.requireShop(shopId), invoiceId, body, RequestUser.id(jwt));
    }
}
