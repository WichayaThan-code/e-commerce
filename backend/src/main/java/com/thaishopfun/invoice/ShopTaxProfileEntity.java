package com.thaishopfun.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_tax_profiles")
class ShopTaxProfileEntity {
    @Id
    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "tax_id", length = 20)
    private String taxId;

    @Column(name = "branch_code", nullable = false, length = 10)
    private String branchCode = "00000";

    @Column(name = "vat_registered", nullable = false)
    private boolean vatRegistered;

    @Column(name = "vat_rate", precision = 7, scale = 4)
    private BigDecimal vatRate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
