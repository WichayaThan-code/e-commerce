package com.thaishopfun.invoice;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Shop tax identity for document issuance.
 * <p>Having a tax ID alone does <em>not</em> mean VAT-registered.
 * VAT rate is never invented: merchant must enter the rate from their RD registration.
 * Reference (informational only): Thailand Revenue Department VAT regime — standard rate historically 7%;
 * this platform does not auto-apply or claim e-Tax compliance.
 */
@Service
public class ShopTaxProfileService {
    public static final String POLICY_NOTE =
        "TAX_INVOICE requires vat_registered=true, tax_id, and merchant-supplied vat_rate from RD registration. "
            + "PDF is not e-Tax. Rate is not hard-coded by the platform.";

    private final JdbcTemplate jdbc;

    public ShopTaxProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.TaxProfileView view(UUID shopId) {
        var rows = jdbc.query(
            """
            SELECT legal_name, tax_id, branch_code, vat_registered, vat_rate, updated_at
              FROM shop_tax_profiles WHERE shop_id = ?
            """,
            (rs, i) -> new ProfileRow(
                rs.getString("legal_name"),
                rs.getString("tax_id"),
                rs.getString("branch_code"),
                rs.getBoolean("vat_registered"),
                rs.getBigDecimal("vat_rate"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant()
            ),
            shopId
        );
        var row = rows.isEmpty() ? null : rows.get(0);
        return toView(row);
    }

    @Transactional
    public InvoiceDtos.TaxProfileView upsert(UUID shopId, InvoiceDtos.UpsertTaxProfileRequest body, UUID actor) {
        var legalName = clean(body.legalName(), 255);
        var taxId = normalizeTaxId(body.taxId());
        var branch = clean(body.branchCode(), 10);
        if (branch == null || branch.isBlank()) branch = "00000";
        boolean vat = body.vatRegistered() != null && body.vatRegistered();
        BigDecimal rate = null;
        if (vat) {
            if (taxId == null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "TAX_PROFILE_INCOMPLETE", "ต้องมีเลขผู้เสียภาษีเมื่อจด VAT", "taxId");
            }
            if (body.vatRate() == null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "VAT_RATE_REQUIRED",
                    "ต้องระบุอัตรา VAT ตามที่จดทะเบียน (ระบบไม่กรอกให้อัตโนมัติ)", "vatRate");
            }
            rate = body.vatRate().setScale(4, RoundingMode.HALF_UP);
            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "อัตรา VAT ต้องอยู่ระหว่าง 0–1", "vatRate");
            }
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM shop_tax_profiles WHERE shop_id = ?", Integer.class, shopId);
        if (exists != null && exists > 0) {
            jdbc.update(
                """
                UPDATE shop_tax_profiles
                   SET legal_name = ?, tax_id = ?, branch_code = ?, vat_registered = ?, vat_rate = ?,
                       updated_at = NOW(), updated_by = ?
                 WHERE shop_id = ?
                """,
                legalName, taxId, branch, vat, rate, actor, shopId
            );
        } else {
            jdbc.update(
                """
                INSERT INTO shop_tax_profiles
                    (shop_id, legal_name, tax_id, branch_code, vat_registered, vat_rate, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), ?)
                """,
                shopId, legalName, taxId, branch, vat, rate, actor
            );
        }
        return view(shopId);
    }

    static InvoiceDtos.TaxProfileView toView(ProfileRow row) {
        if (row == null) {
            return new InvoiceDtos.TaxProfileView(
                null, null, "00000", false, null, false,
                "TAX_PROFILE_MISSING", POLICY_NOTE, null
            );
        }
        String blocked = null;
        boolean can = true;
        if (!row.vatRegistered()) {
            can = false;
            blocked = "NOT_VAT_REGISTERED";
        } else if (row.taxId() == null || row.taxId().isBlank()) {
            can = false;
            blocked = "TAX_ID_MISSING";
        } else if (row.vatRate() == null) {
            can = false;
            blocked = "VAT_RATE_MISSING";
        }
        return new InvoiceDtos.TaxProfileView(
            row.legalName(), row.taxId(), row.branchCode(), row.vatRegistered(), row.vatRate(),
            can, blocked, POLICY_NOTE, row.updatedAt()
        );
    }

    ProfileRow requireRow(UUID shopId) {
        List<ProfileRow> rows = jdbc.query(
            """
            SELECT legal_name, tax_id, branch_code, vat_registered, vat_rate, updated_at
              FROM shop_tax_profiles WHERE shop_id = ?
            """,
            (rs, i) -> new ProfileRow(
                rs.getString("legal_name"),
                rs.getString("tax_id"),
                rs.getString("branch_code"),
                rs.getBoolean("vat_registered"),
                rs.getBigDecimal("vat_rate"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant()
            ),
            shopId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    static String normalizeTaxId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 13) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "เลขผู้เสียภาษีต้องมี 13 หลัก", "taxId");
        }
        return digits;
    }

    static String clean(String value, int max) {
        if (value == null) return null;
        var t = value.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    record ProfileRow(
        String legalName, String taxId, String branchCode, boolean vatRegistered, BigDecimal vatRate, Instant updatedAt
    ) {}
}
