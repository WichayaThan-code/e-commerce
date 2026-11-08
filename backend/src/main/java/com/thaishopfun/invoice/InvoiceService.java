package com.thaishopfun.invoice;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.order.Order;
import com.thaishopfun.order.OrderAddress;
import com.thaishopfun.order.OrderItem;
import com.thaishopfun.order.OrderRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class InvoiceService {
    private static final ZoneId BKK = ZoneId.of("Asia/Bangkok");
    private static final String DISCLAIMER =
        "เอกสารนี้เป็น snapshot ณ เวลาที่ออก — ไม่ใช่ e-Tax และไม่เปลี่ยนสถานะออเดอร์/การชำระเงิน/wallet";

    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final ShopTaxProfileService taxProfiles;

    public InvoiceService(JdbcTemplate jdbc, OrderRepository orders, ShopTaxProfileService taxProfiles) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.taxProfiles = taxProfiles;
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.Page<InvoiceDtos.InvoiceSummary> list(Shop shop, InvoiceDtos.ListFilter filter) {
        int page = Math.max(0, filter.page());
        int size = Math.min(100, Math.max(1, filter.size()));
        boolean byIssued = "issuedAt".equalsIgnoreCase(filter.dateField());
        var where = new StringBuilder(" WHERE shop_id = ? ");
        var args = new ArrayList<Object>();
        args.add(shop.getUuid());
        if (filter.docType() != null && !filter.docType().isBlank()) {
            where.append(" AND doc_type = ? ");
            args.add(filter.docType().trim().toUpperCase(Locale.ROOT));
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            where.append(" AND status = ? ");
            args.add(filter.status().trim().toUpperCase(Locale.ROOT));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" AND (document_number ILIKE ? OR buyer_name ILIKE ? OR source_ref ILIKE ?) ");
            var like = "%" + filter.q().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String dateCol = byIssued ? "issued_at" : "created_at";
        if (filter.from() != null) {
            where.append(" AND ").append(dateCol).append(" >= ? ");
            args.add(Timestamp.from(filter.from().atStartOfDay(BKK).toInstant()));
        }
        if (filter.to() != null) {
            where.append(" AND ").append(dateCol).append(" < ? ");
            args.add(Timestamp.from(filter.to().plusDays(1).atStartOfDay(BKK).toInstant()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM shop_invoices" + where, Long.class, args.toArray());
        long totalItems = total == null ? 0L : total;
        args.add(size);
        args.add(page * size);
        var items = jdbc.query(
            """
            SELECT public_id, doc_type, status, document_number, buyer_name, grand_total, tax_amount, currency,
                   source_type, source_ref, created_at, issued_at
              FROM shop_invoices
            """ + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            (rs, i) -> new InvoiceDtos.InvoiceSummary(
                (UUID) rs.getObject("public_id"),
                rs.getString("doc_type"),
                rs.getString("status"),
                rs.getString("document_number"),
                rs.getString("buyer_name"),
                money(rs.getBigDecimal("grand_total")),
                money(rs.getBigDecimal("tax_amount")),
                rs.getString("currency"),
                rs.getString("source_type"),
                rs.getString("source_ref"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("issued_at") == null ? null : rs.getTimestamp("issued_at").toInstant()
            ),
            args.toArray()
        );
        int totalPages = size == 0 ? 0 : (int) Math.ceil(totalItems / (double) size);
        return new InvoiceDtos.Page<>(items, page, size, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.InvoiceDetail get(Shop shop, UUID publicId) {
        var row = requireRow(shop.getUuid(), publicId);
        return toDetail(row);
    }

    @Transactional
    public InvoiceDtos.InvoiceDetail createFromOrder(Shop shop, InvoiceDtos.CreateFromOrderRequest body, UUID actor) {
        var key = requireKey(body.idempotencyKey());
        var docType = requireDocType(body.docType());
        var existing = findByIdempotency(shop.getUuid(), key);
        if (existing != null) {
            assertSameCreate(existing, docType, body.orderId());
            return toDetail(existing);
        }
        if (body.orderId() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องระบุออเดอร์", "orderId");
        }
        var order = orders.findById(body.orderId())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์"));
        if (!order.getShopId().equals(shop.getUuid())) {
            throw new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์");
        }
        if (docType.equals("TAX_INVOICE")) {
            assertCanIssueTax(shop.getUuid());
        }
        var profile = taxProfiles.requireRow(shop.getUuid());
        var seller = buildSeller(shop, profile, docType);
        var buyer = buildBuyer(order, body.buyerTaxId(), body.buyerEmail());
        var calc = calculate(order, docType, profile);
        var note = ShopTaxProfileService.clean(body.note(), 2000);
        var publicId = UUID.randomUUID();
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                var ps = con.prepareStatement(
                    """
                    INSERT INTO shop_invoices (
                      public_id, shop_id, doc_type, status, currency, source_type, source_id, source_ref,
                      seller_legal_name, seller_tax_id, seller_branch_code, seller_address, seller_vat_registered,
                      buyer_name, buyer_tax_id, buyer_address, buyer_phone, buyer_email,
                      subtotal, discount_total, shipping_total, taxable_base, tax_amount, grand_total, vat_rate_applied,
                      note, idempotency_key, created_by, created_at, updated_at, version
                    ) VALUES (
                      ?, ?, ?, 'DRAFT', ?, 'ORDER', ?, ?,
                      ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, NOW(), NOW(), 0
                    )
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                int i = 1;
                ps.setObject(i++, publicId);
                ps.setObject(i++, shop.getUuid());
                ps.setString(i++, docType);
                ps.setString(i++, order.getCurrency());
                ps.setObject(i++, order.getId());
                ps.setString(i++, order.getOrderNumber());
                ps.setString(i++, seller.legalName());
                ps.setString(i++, seller.taxId());
                ps.setString(i++, seller.branch());
                ps.setString(i++, seller.address());
                ps.setBoolean(i++, seller.vatRegistered());
                ps.setString(i++, buyer.name());
                ps.setString(i++, buyer.taxId());
                ps.setString(i++, buyer.address());
                ps.setString(i++, buyer.phone());
                ps.setString(i++, buyer.email());
                ps.setBigDecimal(i++, calc.subtotal());
                ps.setBigDecimal(i++, calc.discountTotal());
                ps.setBigDecimal(i++, calc.shippingTotal());
                ps.setBigDecimal(i++, calc.taxableBase());
                ps.setBigDecimal(i++, calc.taxAmount());
                ps.setBigDecimal(i++, calc.grandTotal());
                ps.setBigDecimal(i++, calc.vatRate());
                ps.setString(i++, note);
                ps.setString(i++, key);
                ps.setObject(i, actor);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException dup) {
            var again = findByIdempotency(shop.getUuid(), key);
            if (again != null) {
                assertSameCreate(again, docType, body.orderId());
                return toDetail(again);
            }
            throw dup;
        }
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        insertLines(id, calc.lines());
        appendEvent(id, null, "DRAFT", "INVOICE_DRAFT_CREATED", actor, null);
        return toDetail(requireRowById(id));
    }

    @Transactional
    public InvoiceDtos.InvoiceDetail updateDraft(Shop shop, UUID publicId, InvoiceDtos.UpdateDraftRequest body, UUID actor) {
        var row = requireRow(shop.getUuid(), publicId);
        if (!"DRAFT".equals(row.status())) {
            throw new AuthException(HttpStatus.CONFLICT, "INVOICE_NOT_DRAFT", "แก้ไขได้เฉพาะร่าง");
        }
        var name = requireText(body.buyerName(), 255, "buyerName");
        var address = requireText(body.buyerAddress(), 2000, "buyerAddress");
        var taxId = body.buyerTaxId() == null || body.buyerTaxId().isBlank()
            ? null : ShopTaxProfileService.normalizeTaxId(body.buyerTaxId());
        if ("TAX_INVOICE".equals(row.docType()) && taxId == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "BUYER_TAX_ID_REQUIRED", "ใบกำกับภาษีต้องมีเลขผู้เสียภาษีผู้ซื้อ", "buyerTaxId");
        }
        jdbc.update(
            """
            UPDATE shop_invoices
               SET buyer_name = ?, buyer_tax_id = ?, buyer_address = ?, buyer_phone = ?, buyer_email = ?,
                   note = ?, updated_at = NOW(), version = version + 1
             WHERE id = ? AND version = ?
            """,
            name, taxId, address,
            ShopTaxProfileService.clean(body.buyerPhone(), 30),
            ShopTaxProfileService.clean(body.buyerEmail(), 255),
            ShopTaxProfileService.clean(body.note(), 2000),
            row.id(), row.version()
        );
        appendEvent(row.id(), "DRAFT", "DRAFT", "INVOICE_DRAFT_UPDATED", actor, null);
        return toDetail(requireRowById(row.id()));
    }

    @Transactional
    public InvoiceDtos.InvoiceDetail issue(Shop shop, UUID publicId, InvoiceDtos.IssueRequest body, UUID actor) {
        var issueKey = requireKey(body == null ? null : body.idempotencyKey());
        var row = requireRow(shop.getUuid(), publicId);
        if ("ISSUED".equals(row.status())) {
            return toDetail(row);
        }
        if (!"DRAFT".equals(row.status())) {
            throw new AuthException(HttpStatus.CONFLICT, "INVOICE_INVALID_TRANSITION", "ออกเอกสารได้จากร่างเท่านั้น");
        }
        if ("TAX_INVOICE".equals(row.docType())) {
            assertCanIssueTax(shop.getUuid());
            if (row.buyerTaxId() == null || row.buyerTaxId().isBlank()) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "BUYER_TAX_ID_REQUIRED", "ใบกำกับภาษีต้องมีเลขผู้เสียภาษีผู้ซื้อ");
            }
        }
        var year = LocalDate.now(BKK).getYear();
        int seq = nextSeq(shop.getUuid(), row.docType(), year);
        var prefix = row.docType().equals("TAX_INVOICE") ? "TAX" : "INV";
        var number = String.format(Locale.ROOT, "%s-%d-%d-%05d", prefix, shop.getId(), year, seq);
        int updated = jdbc.update(
            """
            UPDATE shop_invoices
               SET status = 'ISSUED', document_number = ?, number_year = ?, number_seq = ?,
                   issued_by = ?, issued_at = NOW(), updated_at = NOW(), version = version + 1
             WHERE id = ? AND status = 'DRAFT' AND version = ?
            """,
            number, year, seq, actor, row.id(), row.version()
        );
        if (updated != 1) {
            throw new AuthException(HttpStatus.CONFLICT, "INVOICE_CONCURRENT", "เอกสารถูกเปลี่ยนโดยผู้อื่น กรุณาโหลดใหม่");
        }
        appendEvent(row.id(), "DRAFT", "ISSUED", "INVOICE_ISSUED", actor, "issueKey=" + issueKey);
        return toDetail(requireRowById(row.id()));
    }

    @Transactional
    public InvoiceDtos.InvoiceDetail cancel(Shop shop, UUID publicId, InvoiceDtos.CancelRequest body, UUID actor) {
        var row = requireRow(shop.getUuid(), publicId);
        if ("CANCELLED".equals(row.status())) return toDetail(row);
        if ("ISSUED".equals(row.status()) && "TAX_INVOICE".equals(row.docType())) {
            // Soft cancel only — number is never reused. Not a credit note / e-Tax void workflow.
            var reason = requireText(body == null ? null : body.reason(), 1000, "reason");
            int updated = jdbc.update(
                """
                UPDATE shop_invoices
                   SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = NOW(), cancel_reason = ?,
                       updated_at = NOW(), version = version + 1
                 WHERE id = ? AND status = 'ISSUED' AND version = ?
                """,
                actor, reason, row.id(), row.version()
            );
            if (updated != 1) {
                throw new AuthException(HttpStatus.CONFLICT, "INVOICE_CONCURRENT", "เอกสารถูกเปลี่ยนโดยผู้อื่น กรุณาโหลดใหม่");
            }
            appendEvent(row.id(), "ISSUED", "CANCELLED", "TAX_INVOICE_MARKED_CANCELLED", actor, reason);
            return toDetail(requireRowById(row.id()));
        }
        if ("DRAFT".equals(row.status()) || "ISSUED".equals(row.status())) {
            var reason = ShopTaxProfileService.clean(body == null ? null : body.reason(), 1000);
            if ("ISSUED".equals(row.status()) && (reason == null || reason.length() < 3)) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องระบุเหตุผลยกเลิก", "reason");
            }
            int updated = jdbc.update(
                """
                UPDATE shop_invoices
                   SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = NOW(), cancel_reason = ?,
                       updated_at = NOW(), version = version + 1
                 WHERE id = ? AND status = ? AND version = ?
                """,
                actor, reason, row.id(), row.status(), row.version()
            );
            if (updated != 1) {
                throw new AuthException(HttpStatus.CONFLICT, "INVOICE_CONCURRENT", "เอกสารถูกเปลี่ยนโดยผู้อื่น กรุณาโหลดใหม่");
            }
            appendEvent(row.id(), row.status(), "CANCELLED", "INVOICE_CANCELLED", actor, reason);
            return toDetail(requireRowById(row.id()));
        }
        throw new AuthException(HttpStatus.CONFLICT, "INVOICE_INVALID_TRANSITION", "ไม่สามารถยกเลิกสถานะนี้ได้");
    }

    private void assertCanIssueTax(UUID shopId) {
        var view = taxProfiles.view(shopId);
        if (!view.canIssueTaxInvoice()) {
            throw new AuthException(HttpStatus.CONFLICT, view.taxInvoiceBlockedReason() == null ? "TAX_INVOICE_BLOCKED" : view.taxInvoiceBlockedReason(),
                "ยังออกใบกำกับภาษีไม่ได้ — ต้องจด VAT มีเลขผู้เสียภาษี และระบุอัตรา VAT ตามที่จดทะเบียน");
        }
    }

    private SellerSnap buildSeller(Shop shop, ShopTaxProfileService.ProfileRow profile, String docType) {
        var legal = profile != null && profile.legalName() != null && !profile.legalName().isBlank()
            ? profile.legalName() : shop.getName();
        var address = formatShopAddress(shop);
        if (address.isBlank()) {
            throw new AuthException(HttpStatus.CONFLICT, "SELLER_ADDRESS_MISSING", "ร้านยังไม่มีที่อยู่สำหรับออกเอกสาร");
        }
        boolean vat = profile != null && profile.vatRegistered();
        String taxId = profile == null ? null : profile.taxId();
        String branch = profile == null ? "00000" : profile.branchCode();
        if ("TAX_INVOICE".equals(docType) && (taxId == null || !vat)) {
            throw new AuthException(HttpStatus.CONFLICT, "TAX_INVOICE_BLOCKED", "โปรไฟล์ภาษียังไม่พร้อมสำหรับใบกำกับภาษี");
        }
        return new SellerSnap(legal, taxId, branch, address, vat);
    }

    private BuyerSnap buildBuyer(Order order, String buyerTaxIdRaw, String buyerEmailRaw) {
        OrderAddress ship = order.getAddresses().stream()
            .filter(a -> "SHIPPING".equalsIgnoreCase(a.getAddressType()))
            .findFirst()
            .orElseGet(() -> order.getAddresses().isEmpty() ? null : order.getAddresses().get(0));
        if (ship == null) {
            throw new AuthException(HttpStatus.CONFLICT, "BUYER_ADDRESS_MISSING", "ออเดอร์ไม่มีที่อยู่ผู้รับ");
        }
        var taxId = buyerTaxIdRaw == null || buyerTaxIdRaw.isBlank()
            ? null : ShopTaxProfileService.normalizeTaxId(buyerTaxIdRaw);
        return new BuyerSnap(
            ship.getRecipientName(),
            taxId,
            formatAddress(ship),
            ship.getPhone(),
            ShopTaxProfileService.clean(buyerEmailRaw, 255)
        );
    }

    private CalcResult calculate(Order order, String docType, ShopTaxProfileService.ProfileRow profile) {
        var lines = new ArrayList<LineCalc>();
        int n = 1;
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = money(order.getDiscountTotal());
        BigDecimal shipping = money(order.getShippingFee()).add(money(order.getCodFee()));
        for (OrderItem item : order.getItems()) {
            var desc = item.getProductNameSnapshot();
            if (item.getOptionLabelSnapshot() != null && !item.getOptionLabelSnapshot().isBlank()) {
                desc = desc + " (" + item.getOptionLabelSnapshot() + ")";
            }
            var lineSub = money(item.getLineTotal());
            subtotal = subtotal.add(lineSub);
            lines.add(new LineCalc(
                n++, desc,
                BigDecimal.valueOf(item.getQuantity()).setScale(3, RoundingMode.HALF_UP),
                money(item.getUnitPrice()),
                money(item.getDiscountAmount()),
                lineSub,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "ORDER_ITEM",
                item.getId()
            ));
        }
        BigDecimal vatRate = null;
        BigDecimal tax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal goodsNet = subtotal; // line totals already net of line discounts; bill discount applied below
        // Allocate order-level discount proportionally across lines for snapshot clarity (no invented tax rules).
        if (discount.compareTo(BigDecimal.ZERO) > 0 && subtotal.compareTo(BigDecimal.ZERO) > 0) {
            var allocated = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            var adjusted = new ArrayList<LineCalc>();
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                BigDecimal share;
                if (i == lines.size() - 1) {
                    share = discount.subtract(allocated);
                } else {
                    share = discount.multiply(line.lineSubtotal()).divide(subtotal, 2, RoundingMode.HALF_UP);
                    allocated = allocated.add(share);
                }
                var newSub = line.lineSubtotal().subtract(share).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                adjusted.add(new LineCalc(
                    line.lineNo(), line.description(), line.quantity(), line.unitPrice(),
                    line.lineDiscount().add(share), newSub, line.taxAmount(), line.sourceType(), line.sourceId()
                ));
            }
            lines = adjusted;
            goodsNet = lines.stream().map(LineCalc::lineSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        }
        if ("TAX_INVOICE".equals(docType)) {
            if (profile == null || profile.vatRate() == null) {
                throw new AuthException(HttpStatus.CONFLICT, "VAT_RATE_MISSING", "ยังไม่ได้ระบุอัตรา VAT ในโปรไฟล์ร้าน");
            }
            vatRate = profile.vatRate().setScale(4, RoundingMode.HALF_UP);
            // Treat stored order money as VAT-inclusive retail totals (platform does not store exclusive base).
            // Derive exclusive base: inclusive / (1+rate), tax = inclusive - base. Documented assumption.
            taxable = goodsNet.add(shipping).divide(BigDecimal.ONE.add(vatRate), 2, RoundingMode.HALF_UP);
            tax = goodsNet.add(shipping).subtract(taxable).setScale(2, RoundingMode.HALF_UP);
            // distribute tax across lines by share of goodsNet
            if (goodsNet.compareTo(BigDecimal.ZERO) > 0) {
                var taxLeft = tax;
                var withTax = new ArrayList<LineCalc>();
                for (int i = 0; i < lines.size(); i++) {
                    var line = lines.get(i);
                    BigDecimal lineTax;
                    if (i == lines.size() - 1) lineTax = taxLeft;
                    else {
                        lineTax = tax.multiply(line.lineSubtotal()).divide(goodsNet, 2, RoundingMode.HALF_UP);
                        taxLeft = taxLeft.subtract(lineTax);
                    }
                    withTax.add(new LineCalc(
                        line.lineNo(), line.description(), line.quantity(), line.unitPrice(),
                        line.lineDiscount(), line.lineSubtotal(), lineTax, line.sourceType(), line.sourceId()
                    ));
                }
                lines = withTax;
            }
        }
        var grand = goodsNet.add(shipping);
        if ("INVOICE".equals(docType)) {
            // Commercial invoice: no VAT claim; amounts mirror order payable.
            taxable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            tax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            vatRate = null;
        }
        return new CalcResult(money(subtotal), discount, shipping, taxable, tax, money(grand), vatRate, lines);
    }

    private void insertLines(long invoiceId, List<LineCalc> lines) {
        for (var line : lines) {
            jdbc.update(
                """
                INSERT INTO shop_invoice_lines
                  (invoice_id, line_no, description, quantity, unit_price, line_discount, line_subtotal, tax_amount, source_type, source_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                invoiceId, line.lineNo(), line.description(), line.quantity(), line.unitPrice(),
                line.lineDiscount(), line.lineSubtotal(), line.taxAmount(), line.sourceType(), line.sourceId()
            );
        }
    }

    private int nextSeq(UUID shopId, String docType, int year) {
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shop_invoice_sequences WHERE shop_id = ? AND doc_type = ? AND year_no = ?",
            Integer.class, shopId, docType, year
        );
        if (existing == null || existing == 0) {
            try {
                jdbc.update(
                    "INSERT INTO shop_invoice_sequences (shop_id, doc_type, year_no, next_seq) VALUES (?, ?, ?, 1)",
                    shopId, docType, year
                );
            } catch (DuplicateKeyException ignored) {
                // concurrent first insert
            }
        }
        var rows = jdbc.query(
            """
            SELECT next_seq FROM shop_invoice_sequences
             WHERE shop_id = ? AND doc_type = ? AND year_no = ?
             FOR UPDATE
            """,
            (rs, i) -> rs.getInt(1),
            shopId, docType, year
        );
        if (rows.isEmpty()) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "INVOICE_SEQUENCE_MISSING", "ไม่พบเลขรันเอกสาร");
        }
        int seq = rows.get(0);
        jdbc.update(
            """
            UPDATE shop_invoice_sequences SET next_seq = next_seq + 1
             WHERE shop_id = ? AND doc_type = ? AND year_no = ?
            """,
            shopId, docType, year
        );
        return seq;
    }

    private InvoiceRow requireRow(UUID shopId, UUID publicId) {
        var rows = jdbc.query(
            "SELECT * FROM shop_invoices WHERE shop_id = ? AND public_id = ?",
            (rs, i) -> mapRow(rs),
            shopId, publicId
        );
        if (rows.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "ไม่พบเอกสาร");
        return rows.get(0);
    }

    private InvoiceRow requireRowById(long id) {
        var rows = jdbc.query("SELECT * FROM shop_invoices WHERE id = ?", (rs, i) -> mapRow(rs), id);
        if (rows.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "ไม่พบเอกสาร");
        return rows.get(0);
    }

    private InvoiceRow findByIdempotency(UUID shopId, String key) {
        var rows = jdbc.query(
            "SELECT * FROM shop_invoices WHERE shop_id = ? AND idempotency_key = ?",
            (rs, i) -> mapRow(rs),
            shopId, key
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void assertSameCreate(InvoiceRow existing, String docType, UUID orderId) {
        if (!existing.docType().equals(docType) || !existing.sourceId().equals(orderId)) {
            throw new AuthException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH", "คีย์ซ้ำแต่ข้อมูลคำขอต่างกัน");
        }
    }

    private InvoiceDtos.InvoiceDetail toDetail(InvoiceRow row) {
        var lines = jdbc.query(
            """
            SELECT line_no, description, quantity, unit_price, line_discount, line_subtotal, tax_amount, source_type, source_id
              FROM shop_invoice_lines WHERE invoice_id = ? ORDER BY line_no
            """,
            (rs, i) -> new InvoiceDtos.InvoiceLineView(
                rs.getInt("line_no"),
                rs.getString("description"),
                rs.getBigDecimal("quantity"),
                money(rs.getBigDecimal("unit_price")),
                money(rs.getBigDecimal("line_discount")),
                money(rs.getBigDecimal("line_subtotal")),
                money(rs.getBigDecimal("tax_amount")),
                rs.getString("source_type"),
                (UUID) rs.getObject("source_id")
            ),
            row.id()
        );
        var events = jdbc.query(
            """
            SELECT from_status, to_status, event_code, note, created_at
              FROM shop_invoice_events WHERE invoice_id = ? ORDER BY created_at ASC, id ASC
            """,
            (rs, i) -> new InvoiceDtos.InvoiceEventView(
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("event_code"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("note")
            ),
            row.id()
        );
        return new InvoiceDtos.InvoiceDetail(
            row.publicId(), row.docType(), row.status(), row.documentNumber(), row.currency(),
            row.sourceType(), row.sourceId(), row.sourceRef(),
            row.sellerLegalName(), row.sellerTaxId(), row.sellerBranchCode(), row.sellerAddress(), row.sellerVatRegistered(),
            row.buyerName(), row.buyerTaxId(), row.buyerAddress(), row.buyerPhone(), row.buyerEmail(),
            row.subtotal(), row.discountTotal(), row.shippingTotal(), row.taxableBase(), row.taxAmount(), row.grandTotal(),
            row.vatRateApplied(), row.note(), row.createdAt(), row.issuedAt(), row.cancelledAt(), row.cancelReason(),
            lines, events, DISCLAIMER
        );
    }

    private void appendEvent(long invoiceId, String from, String to, String code, UUID actor, String note) {
        jdbc.update(
            """
            INSERT INTO shop_invoice_events (invoice_id, from_status, to_status, event_code, actor_user_id, note, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """,
            invoiceId, from, to, code, actor, note
        );
    }

    private static InvoiceRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InvoiceRow(
            rs.getLong("id"),
            (UUID) rs.getObject("public_id"),
            (UUID) rs.getObject("shop_id"),
            rs.getString("doc_type"),
            rs.getString("status"),
            rs.getString("document_number"),
            rs.getString("currency"),
            rs.getString("source_type"),
            (UUID) rs.getObject("source_id"),
            rs.getString("source_ref"),
            rs.getString("seller_legal_name"),
            rs.getString("seller_tax_id"),
            rs.getString("seller_branch_code"),
            rs.getString("seller_address"),
            rs.getBoolean("seller_vat_registered"),
            rs.getString("buyer_name"),
            rs.getString("buyer_tax_id"),
            rs.getString("buyer_address"),
            rs.getString("buyer_phone"),
            rs.getString("buyer_email"),
            money(rs.getBigDecimal("subtotal")),
            money(rs.getBigDecimal("discount_total")),
            money(rs.getBigDecimal("shipping_total")),
            money(rs.getBigDecimal("taxable_base")),
            money(rs.getBigDecimal("tax_amount")),
            money(rs.getBigDecimal("grand_total")),
            rs.getBigDecimal("vat_rate_applied"),
            rs.getString("note"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("issued_at") == null ? null : rs.getTimestamp("issued_at").toInstant(),
            rs.getTimestamp("cancelled_at") == null ? null : rs.getTimestamp("cancelled_at").toInstant(),
            rs.getString("cancel_reason"),
            rs.getLong("version")
        );
    }

    private static String formatShopAddress(Shop shop) {
        var parts = new ArrayList<String>();
        if (shop.getAddress() != null && !shop.getAddress().isBlank()) parts.add(shop.getAddress().trim());
        if (shop.getSubdistrict() != null && !shop.getSubdistrict().isBlank()) parts.add(shop.getSubdistrict().trim());
        if (shop.getDistrict() != null && !shop.getDistrict().isBlank()) parts.add(shop.getDistrict().trim());
        if (shop.getProvince() != null && !shop.getProvince().isBlank()) parts.add(shop.getProvince().trim());
        if (shop.getPostalCode() != null && !shop.getPostalCode().isBlank()) parts.add(shop.getPostalCode().trim());
        return String.join(" ", parts);
    }

    private static String formatAddress(OrderAddress a) {
        var parts = new ArrayList<String>();
        parts.add(a.getAddressLine1());
        if (a.getAddressLine2() != null && !a.getAddressLine2().isBlank()) parts.add(a.getAddressLine2());
        if (a.getSubdistrict() != null && !a.getSubdistrict().isBlank()) parts.add(a.getSubdistrict());
        parts.add(a.getDistrict());
        parts.add(a.getProvince());
        parts.add(a.getPostalCode());
        return String.join(" ", parts);
    }

    private static String requireDocType(String raw) {
        if (raw == null) throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องระบุประเภทเอกสาร", "docType");
        var t = raw.trim().toUpperCase(Locale.ROOT);
        if (!t.equals("INVOICE") && !t.equals("TAX_INVOICE")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ประเภทเอกสารไม่รองรับ", "docType");
        }
        return t;
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องระบุ idempotencyKey", "idempotencyKey");
        }
        return key.trim();
    }

    private static String requireText(String value, int max, String field) {
        var t = ShopTaxProfileService.clean(value, max);
        if (t == null) throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องกรอกข้อมูล", field);
        return t;
    }

    static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private record SellerSnap(String legalName, String taxId, String branch, String address, boolean vatRegistered) {}
    private record BuyerSnap(String name, String taxId, String address, String phone, String email) {}
    private record LineCalc(
        int lineNo, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineDiscount,
        BigDecimal lineSubtotal, BigDecimal taxAmount, String sourceType, UUID sourceId
    ) {}
    private record CalcResult(
        BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingTotal, BigDecimal taxableBase,
        BigDecimal taxAmount, BigDecimal grandTotal, BigDecimal vatRate, List<LineCalc> lines
    ) {}
    private record InvoiceRow(
        long id, UUID publicId, UUID shopId, String docType, String status, String documentNumber, String currency,
        String sourceType, UUID sourceId, String sourceRef,
        String sellerLegalName, String sellerTaxId, String sellerBranchCode, String sellerAddress, boolean sellerVatRegistered,
        String buyerName, String buyerTaxId, String buyerAddress, String buyerPhone, String buyerEmail,
        BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingTotal, BigDecimal taxableBase,
        BigDecimal taxAmount, BigDecimal grandTotal, BigDecimal vatRateApplied, String note,
        Instant createdAt, Instant issuedAt, Instant cancelledAt, String cancelReason, long version
    ) {}
}
