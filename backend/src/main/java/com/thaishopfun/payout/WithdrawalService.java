package com.thaishopfun.payout;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.payment.PaymentAccountService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WithdrawalService {
    private static final Set<String> CANCELABLE = Set.of("REQUESTED");
    private static final Set<String> REJECTABLE = Set.of("REQUESTED", "APPROVED", "PROCESSING", "NEEDS_REVIEW");
    private static final Set<String> APPROVABLE = Set.of("REQUESTED");
    private static final Set<String> PROCESSABLE = Set.of("APPROVED");
    private static final Set<String> PAYABLE = Set.of("APPROVED", "PROCESSING", "NEEDS_REVIEW");
    private static final Set<String> REVIEWABLE = Set.of("APPROVED", "PROCESSING");

    private final JdbcTemplate jdbc;
    private final ShopWalletService wallets;
    private final PaymentAccountService accounts;

    public WithdrawalService(JdbcTemplate jdbc, ShopWalletService wallets, PaymentAccountService accounts) {
        this.jdbc = jdbc;
        this.wallets = wallets;
        this.accounts = accounts;
    }

    @Transactional
    public PayoutDtos.WithdrawalView request(Shop shop, PayoutDtos.CreateWithdrawalRequest body, UUID actor) {
        if (body == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ข้อมูลคำขอจำเป็น");
        }
        var amount = ShopWalletService.requirePositiveMoney(body.amount());
        var currency = ShopWalletService.requireCurrency(body.currency());
        var key = ShopWalletService.requireIdempotencyKey(body.idempotencyKey());
        var provider = requireProvider(body.accountProvider());

        var existingId = jdbc.query(
            "SELECT id FROM shop_withdrawal_requests WHERE shop_id = ? AND idempotency_key = ?",
            (rs, i) -> rs.getLong("id"), shop.getUuid(), key
        );
        if (!existingId.isEmpty()) {
            var existing = requireById(existingId.get(0));
            if (existing.amount().compareTo(amount) != 0
                || !existing.currency().equals(currency)
                || !existing.accountProvider().equals(provider)) {
                throw new AuthException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH",
                    "ใช้ idempotency key เดิมกับข้อมูลที่ต่างกัน");
            }
            return toView(existing, true);
        }

        var account = snapshotAccount(shop.getUuid(), provider);
        var publicId = UUID.randomUUID();
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                var ps = con.prepareStatement(
                    """
                    INSERT INTO shop_withdrawal_requests (
                      public_id, shop_id, amount, currency, status, idempotency_key,
                      account_provider, account_bank_name, account_name, account_number_masked,
                      account_number_last4, account_promptpay_masked, requested_by, requested_at, version
                    ) VALUES (?, ?, ?, ?, 'REQUESTED', ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 0)
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setObject(1, publicId);
                ps.setObject(2, shop.getUuid());
                ps.setBigDecimal(3, amount);
                ps.setString(4, currency);
                ps.setString(5, key);
                ps.setString(6, account.provider());
                ps.setString(7, account.bankName());
                ps.setString(8, account.accountName());
                ps.setString(9, account.numberMasked());
                ps.setString(10, account.last4());
                ps.setString(11, account.promptPayMasked());
                ps.setObject(12, actor);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException dup) {
            var again = jdbc.query(
                "SELECT id FROM shop_withdrawal_requests WHERE shop_id = ? AND idempotency_key = ?",
                (rs, i) -> rs.getLong("id"), shop.getUuid(), key
            );
            if (!again.isEmpty()) return toView(requireById(again.get(0)), true);
            throw dup;
        }
        long id = requireGeneratedId(keyHolder);
        wallets.reserveForWithdrawal(shop.getUuid(), amount, currency, publicId.toString(), "wd-reserve:" + key, actor);
        appendEvent(id, null, "REQUESTED", "WITHDRAWAL_REQUESTED", actor, null);
        return toView(requireById(id), true);
    }

    @Transactional
    public PayoutDtos.WithdrawalView cancel(Shop shop, UUID publicId, UUID actor) {
        var row = requireByPublicId(publicId);
        if (!row.shopId().equals(shop.getUuid())) {
            throw new AuthException(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND", "ไม่พบคำขอถอน");
        }
        if (!CANCELABLE.contains(row.status())) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_NOT_CANCELABLE",
                "ยกเลิกได้เฉพาะคำขอที่ยังไม่เริ่มดำเนินการจ่าย");
        }
        releaseOnce(row, actor, "ยกเลิกโดยร้าน", "wd-release-cancel:" + row.idempotencyKey());
        updateStatus(row.id(), row.version(), "CANCELLED", Map.of(
            "cancelled_by", actor,
            "cancelled_at", Timestamp.from(Instant.now())
        ));
        appendEvent(row.id(), row.status(), "CANCELLED", "WITHDRAWAL_CANCELLED", actor, null);
        return toView(requireById(row.id()), true);
    }

    @Transactional
    public PayoutDtos.WithdrawalView approve(UUID publicId, UUID actor) {
        var row = requireByPublicId(publicId);
        if (!APPROVABLE.contains(row.status())) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_INVALID_TRANSITION", "อนุมัติได้เฉพาะสถานะ REQUESTED");
        }
        updateStatus(row.id(), row.version(), "APPROVED", Map.of(
            "reviewed_by", actor,
            "reviewed_at", Timestamp.from(Instant.now())
        ));
        appendEvent(row.id(), row.status(), "APPROVED", "WITHDRAWAL_APPROVED", actor, null);
        return toView(requireById(row.id()), true);
    }

    @Transactional
    public PayoutDtos.WithdrawalView markProcessing(UUID publicId, UUID actor) {
        var row = requireByPublicId(publicId);
        if (!PROCESSABLE.contains(row.status())) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_INVALID_TRANSITION", "เริ่มโอนได้จากสถานะ APPROVED เท่านั้น");
        }
        updateStatus(row.id(), row.version(), "PROCESSING", Map.of());
        appendEvent(row.id(), row.status(), "PROCESSING", "WITHDRAWAL_PROCESSING", actor, null);
        return toView(requireById(row.id()), true);
    }

    @Transactional
    public PayoutDtos.WithdrawalView reject(UUID publicId, String reason, UUID actor) {
        var row = requireByPublicId(publicId);
        if (!REJECTABLE.contains(row.status()) || "PAID".equals(row.status()) || "CANCELLED".equals(row.status()) || "REJECTED".equals(row.status())) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_INVALID_TRANSITION", "ปฏิเสธคำขอนี้ไม่ได้");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องระบุเหตุผลปฏิเสธ", "reason");
        }
        releaseOnce(row, actor, reason.trim(), "wd-release-reject:" + row.idempotencyKey());
        updateStatus(row.id(), row.version(), "REJECTED", Map.of(
            "reviewed_by", actor,
            "reviewed_at", Timestamp.from(Instant.now()),
            "reject_reason", reason.trim()
        ));
        appendEvent(row.id(), row.status(), "REJECTED", "WITHDRAWAL_REJECTED", actor, reason.trim());
        return toView(requireById(row.id()), true);
    }

    @Transactional
    public PayoutDtos.WithdrawalView needsReview(UUID publicId, String reason, UUID actor) {
        var row = requireByPublicId(publicId);
        if (!REVIEWABLE.contains(row.status())) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_INVALID_TRANSITION", "ส่งเข้าตรวจสอบได้จาก APPROVED/PROCESSING เท่านั้น");
        }
        if (reason == null || reason.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องระบุเหตุผล", "reason");
        }
        // Keep reserved funds — do not release on uncertain transfer outcome.
        updateStatus(row.id(), row.version(), "NEEDS_REVIEW", Map.of());
        appendEvent(row.id(), row.status(), "NEEDS_REVIEW", "WITHDRAWAL_NEEDS_REVIEW", actor, reason.trim());
        return toView(requireById(row.id()), true);
    }

    @Transactional
    public PayoutDtos.WithdrawalView markPaid(UUID publicId, PayoutDtos.MarkPaidRequest body, UUID actor) {
        if (body == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ข้อมูลผลโอนจำเป็น");
        }
        var row = requireByPublicId(publicId);
        if (!PAYABLE.contains(row.status())) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_INVALID_TRANSITION", "บันทึกจ่ายแล้วได้เฉพาะคำขอที่รอดำเนินการ");
        }
        var transferAmount = body.transferAmount() == null ? row.amount() : ShopWalletService.requirePositiveMoney(body.transferAmount());
        if (transferAmount.compareTo(row.amount()) != 0) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSFER_AMOUNT_MISMATCH", "ยอดโอนต้องเท่ากับยอดคำขอ");
        }
        if (body.transferReference() == null || body.transferReference().isBlank() || body.transferReference().length() > 128) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องมีเลขอ้างอิงการโอน", "transferReference");
        }
        if (body.transferDate() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ต้องมีวันที่โอน", "transferDate");
        }
        var externalKey = body.externalTransferKey() == null || body.externalTransferKey().isBlank()
            ? null : body.externalTransferKey().trim();
        if (externalKey != null && externalKey.length() > 128) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "externalTransferKey ยาวเกิน", "externalTransferKey");
        }
        if (externalKey != null) {
            var clash = jdbc.query(
                "SELECT public_id FROM shop_withdrawal_requests WHERE external_transfer_key = ? AND id <> ?",
                (rs, i) -> (UUID) rs.getObject("public_id"), externalKey, row.id()
            );
            if (!clash.isEmpty()) {
                throw new AuthException(HttpStatus.CONFLICT, "TRANSFER_REFERENCE_REUSED",
                    "รหัสธุรกรรมธนาคารนี้ถูกใช้ยืนยันคำขออื่นแล้ว");
            }
        }
        wallets.capturePayout(row.shopId(), row.amount(), row.currency(), row.publicId().toString(),
            "wd-paid:" + row.idempotencyKey(), actor, body.note());
        try {
            updateStatus(row.id(), row.version(), "PAID", Map.of(
                "paid_by", actor,
                "paid_at", Timestamp.from(Instant.now()),
                "transfer_reference", body.transferReference().trim(),
                "transfer_amount", transferAmount,
                "transfer_date", java.sql.Date.valueOf(body.transferDate()),
                "external_transfer_key", externalKey
            ));
        } catch (DuplicateKeyException dup) {
            throw new AuthException(HttpStatus.CONFLICT, "TRANSFER_REFERENCE_REUSED",
                "รหัสธุรกรรมธนาคารนี้ถูกใช้ยืนยันคำขออื่นแล้ว");
        }
        appendEvent(row.id(), row.status(), "PAID", "WITHDRAWAL_PAID", actor, body.note());
        return toView(requireById(row.id()), true);
    }

    @Transactional
    public PayoutDtos.WalletBalance credit(Shop shop, PayoutDtos.CreditRequest body, UUID actor) {
        if (body == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ข้อมูลเครดิตจำเป็น");
        }
        return wallets.creditPlatformHolding(
            shop.getUuid(),
            body.amount(),
            body.currency(),
            body.idempotencyKey(),
            actor,
            body.note(),
            body.sourceReference()
        );
    }

    @Transactional(readOnly = true)
    public PayoutDtos.Page<PayoutDtos.WithdrawalView> listForShop(UUID shopUuid, Long shopNo, String shopName,
                                                                  String status, LocalDate from, LocalDate to,
                                                                  boolean filterPaidAt, int page, int size, boolean withEvents) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        var where = new StringBuilder(" WHERE shop_id = ? ");
        var args = new java.util.ArrayList<Object>();
        args.add(shopUuid);
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ? ");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (from != null) {
            where.append(filterPaidAt ? " AND transfer_date >= ? " : " AND requested_at >= ? ");
            args.add(filterPaidAt ? java.sql.Date.valueOf(from) : Timestamp.from(from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        }
        if (to != null) {
            where.append(filterPaidAt ? " AND transfer_date <= ? " : " AND requested_at < ? ");
            if (filterPaidAt) args.add(java.sql.Date.valueOf(to));
            else args.add(Timestamp.from(to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM shop_withdrawal_requests" + where, Long.class, args.toArray());
        long totalItems = total == null ? 0 : total;
        args.add(safeSize);
        args.add(safePage * safeSize);
        var items = jdbc.query(
            "SELECT * FROM shop_withdrawal_requests" + where + " ORDER BY requested_at DESC, id DESC LIMIT ? OFFSET ?",
            (rs, i) -> mapRow(rs),
            args.toArray()
        ).stream().map(r -> toView(r, shopNo, shopName, withEvents)).toList();
        int totalPages = (int) Math.ceil(totalItems / (double) safeSize);
        return new PayoutDtos.Page<>(items, safePage, safeSize, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public PayoutDtos.Page<PayoutDtos.WithdrawalView> listAdmin(String status, Long shopNo, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        var where = new StringBuilder(" WHERE 1=1 ");
        var args = new java.util.ArrayList<Object>();
        if (status != null && !status.isBlank()) {
            where.append(" AND w.status = ? ");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (shopNo != null) {
            where.append(" AND s.shop_no = ? ");
            args.add(shopNo);
        }
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM shop_withdrawal_requests w JOIN shops s ON s.id = w.shop_id" + where,
            Long.class, args.toArray()
        );
        long totalItems = total == null ? 0 : total;
        args.add(safeSize);
        args.add(safePage * safeSize);
        var items = jdbc.query(
            """
            SELECT w.*, s.shop_no AS shop_no, s.name AS shop_name
              FROM shop_withdrawal_requests w
              JOIN shops s ON s.id = w.shop_id
            """ + where + " ORDER BY w.requested_at DESC, w.id DESC LIMIT ? OFFSET ?",
            (rs, i) -> {
                var row = mapRow(rs);
                return toView(row, rs.getLong("shop_no"), rs.getString("shop_name"), false);
            },
            args.toArray()
        );
        int totalPages = (int) Math.ceil(totalItems / (double) safeSize);
        return new PayoutDtos.Page<>(items, safePage, safeSize, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public PayoutDtos.WithdrawalView get(UUID publicId, UUID expectedShopIdOrNull) {
        var row = requireByPublicId(publicId);
        if (expectedShopIdOrNull != null && !row.shopId().equals(expectedShopIdOrNull)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND", "ไม่พบคำขอถอน");
        }
        var shopMeta = jdbc.query(
            "SELECT shop_no, name FROM shops WHERE id = ?",
            (rs, i) -> Map.of("shopNo", rs.getLong("shop_no"), "name", rs.getString("name")),
            row.shopId()
        ).get(0);
        return toView(row, (Long) shopMeta.get("shopNo"), (String) shopMeta.get("name"), true);
    }

    private void releaseOnce(WithdrawalRow row, UUID actor, String note, String idempotencyKey) {
        wallets.releaseReservation(row.shopId(), row.amount(), row.currency(), row.publicId().toString(), idempotencyKey, actor, note);
    }

    private AccountSnapshot snapshotAccount(UUID shopId, String provider) {
        var list = accounts.list(shopId, false);
        var match = list.stream()
            .filter(a -> provider.equals(a.provider()) && a.active())
            .findFirst()
            .orElseThrow(() -> new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "PAYOUT_ACCOUNT_MISSING",
                "ยังไม่ได้ตั้งค่าบัญชีรับเงินที่ใช้งานได้"));
        if ("BANK_TRANSFER".equals(provider)) {
            if (blank(match.accountNumber()) || blank(match.accountName())) {
                throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "PAYOUT_ACCOUNT_INCOMPLETE", "บัญชีธนาคารไม่ครบ");
            }
            return new AccountSnapshot(provider, match.bankName(), match.accountName(),
                PaymentAccountService.mask(match.accountNumber()), last4(match.accountNumber()), null);
        }
        if (blank(match.promptPayId())) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "PAYOUT_ACCOUNT_INCOMPLETE", "PromptPay ไม่ครบ");
        }
        return new AccountSnapshot(provider, match.bankName(), match.accountName(),
            PaymentAccountService.mask(match.promptPayId()), last4(match.promptPayId()),
            PaymentAccountService.mask(match.promptPayId()));
    }

    private WithdrawalRow requireByPublicId(UUID publicId) {
        var rows = jdbc.query("SELECT * FROM shop_withdrawal_requests WHERE public_id = ?", (rs, i) -> mapRow(rs), publicId);
        if (rows.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND", "ไม่พบคำขอถอน");
        return rows.get(0);
    }

    private WithdrawalRow requireById(long id) {
        var rows = jdbc.query("SELECT * FROM shop_withdrawal_requests WHERE id = ?", (rs, i) -> mapRow(rs), id);
        if (rows.isEmpty()) throw new AuthException(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND", "ไม่พบคำขอถอน");
        return rows.get(0);
    }

    private void updateStatus(long id, long version, String status, Map<String, Object> extra) {
        var sql = new StringBuilder("UPDATE shop_withdrawal_requests SET status = ?, version = version + 1");
        var args = new java.util.ArrayList<>();
        args.add(status);
        for (var e : extra.entrySet()) {
            sql.append(", ").append(e.getKey()).append(" = ?");
            args.add(e.getValue());
        }
        sql.append(" WHERE id = ? AND version = ?");
        args.add(id);
        args.add(version);
        int n = jdbc.update(sql.toString(), args.toArray());
        if (n != 1) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_CONCURRENT_UPDATE", "คำขอถูกอัปเดตพร้อมกัน กรุณาลองใหม่");
        }
    }

    private void appendEvent(long withdrawalId, String from, String to, String code, UUID actor, String note) {
        jdbc.update(
            """
            INSERT INTO shop_withdrawal_events (withdrawal_id, from_status, to_status, event_code, actor_user_id, note, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """,
            withdrawalId, from, to, code, actor, note
        );
    }

    private PayoutDtos.WithdrawalView toView(WithdrawalRow row, boolean withEvents) {
        var shopMeta = jdbc.query(
            "SELECT shop_no, name FROM shops WHERE id = ?",
            (rs, i) -> Map.of("shopNo", rs.getLong("shop_no"), "name", rs.getString("name")),
            row.shopId()
        ).get(0);
        return toView(row, (Long) shopMeta.get("shopNo"), (String) shopMeta.get("name"), withEvents);
    }

    private PayoutDtos.WithdrawalView toView(WithdrawalRow row, Long shopNo, String shopName, boolean withEvents) {
        List<PayoutDtos.WithdrawalEventView> events = List.of();
        if (withEvents) {
            events = jdbc.query(
                """
                SELECT from_status, to_status, event_code, created_at, note
                  FROM shop_withdrawal_events WHERE withdrawal_id = ? ORDER BY created_at ASC, id ASC
                """,
                (rs, i) -> new PayoutDtos.WithdrawalEventView(
                    rs.getString("from_status"),
                    rs.getString("to_status"),
                    rs.getString("event_code"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getString("note")
                ),
                row.id()
            );
        }
        return new PayoutDtos.WithdrawalView(
            row.publicId(), shopNo, shopName, row.amount(), row.currency(), row.status(),
            row.accountProvider(), row.accountBankName(), row.accountName(), row.accountNumberMasked(),
            row.accountPromptPayMasked(), row.requestedAt(), row.reviewedAt(), row.rejectReason(),
            row.paidAt(), row.transferReference(), row.transferAmount(), row.transferDate(),
            row.cancelledAt(), events
        );
    }

    private static WithdrawalRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WithdrawalRow(
            rs.getLong("id"),
            (UUID) rs.getObject("public_id"),
            (UUID) rs.getObject("shop_id"),
            ShopWalletService.money(rs.getBigDecimal("amount")),
            rs.getString("currency"),
            rs.getString("status"),
            rs.getString("idempotency_key"),
            rs.getString("account_provider"),
            rs.getString("account_bank_name"),
            rs.getString("account_name"),
            rs.getString("account_number_masked"),
            rs.getString("account_promptpay_masked"),
            rs.getTimestamp("requested_at").toInstant(),
            rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
            rs.getString("reject_reason"),
            rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant(),
            rs.getString("transfer_reference"),
            rs.getBigDecimal("transfer_amount") == null ? null : ShopWalletService.money(rs.getBigDecimal("transfer_amount")),
            rs.getDate("transfer_date") == null ? null : rs.getDate("transfer_date").toLocalDate(),
            rs.getTimestamp("cancelled_at") == null ? null : rs.getTimestamp("cancelled_at").toInstant(),
            rs.getLong("version")
        );
    }

    private static String requireProvider(String raw) {
        var provider = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!"BANK_TRANSFER".equals(provider) && !"PROMPTPAY".equals(provider)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "บัญชีรับเงินต้องเป็น BANK_TRANSFER หรือ PROMPTPAY", "accountProvider");
        }
        return provider;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String last4(String value) {
        if (value == null || value.length() < 4) return value;
        return value.substring(value.length() - 4);
    }

    private static long requireGeneratedId(GeneratedKeyHolder keyHolder) {
        var key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("missing generated id");
        return key.longValue();
    }

    private record AccountSnapshot(
        String provider, String bankName, String accountName, String numberMasked, String last4, String promptPayMasked
    ) {}

    private record WithdrawalRow(
        long id, UUID publicId, UUID shopId, BigDecimal amount, String currency, String status, String idempotencyKey,
        String accountProvider, String accountBankName, String accountName, String accountNumberMasked,
        String accountPromptPayMasked, Instant requestedAt, Instant reviewedAt, String rejectReason,
        Instant paidAt, String transferReference, BigDecimal transferAmount, LocalDate transferDate,
        Instant cancelledAt, long version
    ) {}
}
