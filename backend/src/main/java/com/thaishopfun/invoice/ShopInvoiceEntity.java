package com.thaishopfun.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "shop_invoices",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_shop_invoice_idempotency", columnNames = {"shop_id", "idempotency_key"}),
        @UniqueConstraint(name = "uq_shop_invoice_number", columnNames = {"shop_id", "doc_type", "document_number"})
    }
)
class ShopInvoiceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "document_number", length = 40)
    private String documentNumber;

    @Column(name = "number_year")
    private Integer numberYear;

    @Column(name = "number_seq")
    private Integer numberSeq;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "THB";

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "source_ref", length = 64)
    private String sourceRef;

    @Column(name = "seller_legal_name", nullable = false, length = 255)
    private String sellerLegalName;

    @Column(name = "seller_tax_id", length = 20)
    private String sellerTaxId;

    @Column(name = "seller_branch_code", length = 10)
    private String sellerBranchCode;

    @Column(name = "seller_address", nullable = false, columnDefinition = "text")
    private String sellerAddress;

    @Column(name = "seller_vat_registered", nullable = false)
    private boolean sellerVatRegistered;

    @Column(name = "buyer_name", nullable = false, length = 255)
    private String buyerName;

    @Column(name = "buyer_tax_id", length = 20)
    private String buyerTaxId;

    @Column(name = "buyer_address", nullable = false, columnDefinition = "text")
    private String buyerAddress;

    @Column(name = "buyer_phone", length = 30)
    private String buyerPhone;

    @Column(name = "buyer_email", length = 255)
    private String buyerEmail;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "shipping_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingTotal = BigDecimal.ZERO;

    @Column(name = "taxable_base", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxableBase = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "vat_rate_applied", precision = 7, scale = 4)
    private BigDecimal vatRateApplied;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Version
    @Column(nullable = false)
    private long version;
}
