package com.thaishopfun.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InvoiceDtos {
    private InvoiceDtos() {}

    public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record TaxProfileView(
        String legalName,
        String taxId,
        String branchCode,
        boolean vatRegistered,
        BigDecimal vatRate,
        boolean canIssueTaxInvoice,
        String taxInvoiceBlockedReason,
        String policyNote,
        Instant updatedAt
    ) {}

    public record UpsertTaxProfileRequest(
        String legalName,
        String taxId,
        String branchCode,
        Boolean vatRegistered,
        BigDecimal vatRate
    ) {}

    public record InvoiceLineView(
        int lineNo,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineDiscount,
        BigDecimal lineSubtotal,
        BigDecimal taxAmount,
        String sourceType,
        UUID sourceId
    ) {}

    public record InvoiceEventView(
        String fromStatus,
        String toStatus,
        String eventCode,
        Instant at,
        String note
    ) {}

    public record InvoiceSummary(
        UUID id,
        String docType,
        String status,
        String documentNumber,
        String buyerName,
        BigDecimal grandTotal,
        BigDecimal taxAmount,
        String currency,
        String sourceType,
        String sourceRef,
        Instant createdAt,
        Instant issuedAt
    ) {}

    public record InvoiceDetail(
        UUID id,
        String docType,
        String status,
        String documentNumber,
        String currency,
        String sourceType,
        UUID sourceId,
        String sourceRef,
        String sellerLegalName,
        String sellerTaxId,
        String sellerBranchCode,
        String sellerAddress,
        boolean sellerVatRegistered,
        String buyerName,
        String buyerTaxId,
        String buyerAddress,
        String buyerPhone,
        String buyerEmail,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal shippingTotal,
        BigDecimal taxableBase,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        BigDecimal vatRateApplied,
        String note,
        Instant createdAt,
        Instant issuedAt,
        Instant cancelledAt,
        String cancelReason,
        List<InvoiceLineView> lines,
        List<InvoiceEventView> events,
        String disclaimer
    ) {}

    public record CreateFromOrderRequest(
        UUID orderId,
        String docType,
        String buyerTaxId,
        String buyerEmail,
        String note,
        String idempotencyKey
    ) {}

    public record UpdateDraftRequest(
        String buyerName,
        String buyerTaxId,
        String buyerAddress,
        String buyerPhone,
        String buyerEmail,
        String note
    ) {}

    public record IssueRequest(String idempotencyKey) {}

    public record CancelRequest(String reason) {}

    public record ListFilter(
        String docType,
        String status,
        String q,
        LocalDate from,
        LocalDate to,
        String dateField,
        int page,
        int size
    ) {}
}
