package com.thaishopfun.payout;

import com.thaishopfun.auth.AuthException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only shop wallet ledger for <em>platform-held</em> funds only.
 * Never credits from order GMV / BANK_TRANSFER / PromptPay / COD — those settle to the merchant directly.
 */
@Service
public class ShopWalletService {
    public static final String CURRENCY_THB = "THB";
    public static final String HOLDING_POLICY =
        "PLATFORM_HELD_ONLY: current channels BANK_TRANSFER/PROMPTPAY/COD settle to the merchant; "
            + "wallet credits require PLATFORM_HOLDING_CREDIT (or a future gateway settlement source).";

    public static final String REASON_PLATFORM_HOLDING_CREDIT = "PLATFORM_HOLDING_CREDIT";
    public static final String REASON_WITHDRAWAL_RESERVE = "WITHDRAWAL_RESERVE";
    public static final String REASON_WITHDRAWAL_RELEASE = "WITHDRAWAL_RELEASE";
    public static final String REASON_WITHDRAWAL_PAID = "WITHDRAWAL_PAID";

    private final JdbcTemplate jdbc;

    public ShopWalletService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PayoutDtos.WalletBalance balance(UUID shopId) {
        // Read path must not create rows (Postgres rejects INSERT in read-only TX).
        // Wallet rows are created lazily on credit/reserve via ensureWallet.
        var rows = jdbc.query(
            "SELECT currency, available, reserved FROM shop_wallets WHERE shop_id = ?",
            (rs, i) -> new WalletRow(
                rs.getString("currency"),
                money(rs.getBigDecimal("available")),
                money(rs.getBigDecimal("reserved")),
                0L
            ),
            shopId
        );
        var row = rows.isEmpty()
            ? new WalletRow(CURRENCY_THB, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0L)
            : rows.get(0);
        var pending = jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(amount), 0)
              FROM shop_withdrawal_requests
             WHERE shop_id = ? AND status IN ('REQUESTED','APPROVED','PROCESSING','NEEDS_REVIEW')
            """,
            BigDecimal.class, shopId
        );
        return new PayoutDtos.WalletBalance(
            money(row.available()),
            money(row.reserved()),
            money(pending),
            row.currency(),
            HOLDING_POLICY,
            row.available().compareTo(BigDecimal.ZERO) > 0
        );
    }

    @Transactional(readOnly = true)
    public PayoutDtos.Page<PayoutDtos.LedgerEntryView> ledger(UUID shopId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM shop_wallet_ledger WHERE shop_id = ?", Long.class, shopId);
        long totalItems = total == null ? 0L : total;
        var items = jdbc.query(
            """
            SELECT id, entry_type, reason_code, amount, currency,
                   balance_available_after, balance_reserved_after, source_type, source_id, created_at
              FROM shop_wallet_ledger
             WHERE shop_id = ?
             ORDER BY created_at DESC, id DESC
             LIMIT ? OFFSET ?
            """,
            (rs, i) -> new PayoutDtos.LedgerEntryView(
                rs.getLong("id"),
                rs.getString("entry_type"),
                rs.getString("reason_code"),
                money(rs.getBigDecimal("amount")),
                rs.getString("currency"),
                money(rs.getBigDecimal("balance_available_after")),
                money(rs.getBigDecimal("balance_reserved_after")),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getTimestamp("created_at").toInstant()
            ),
            shopId, safeSize, safePage * safeSize
        );
        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil(totalItems / (double) safeSize);
        return new PayoutDtos.Page<>(items, safePage, safeSize, totalItems, totalPages);
    }

    @Transactional
    public PayoutDtos.WalletBalance creditPlatformHolding(
        UUID shopId, BigDecimal amount, String currency, String idempotencyKey, UUID actor, String note, String sourceReference
    ) {
        var money = requirePositiveMoney(amount);
        var cur = requireCurrency(currency);
        var key = requireIdempotencyKey(idempotencyKey);
        ensureWallet(shopId);
        var existing = findLedgerByIdempotency(shopId, key);
        if (existing != null) {
            assertSameLedgerPayload(existing, "CREDIT", REASON_PLATFORM_HOLDING_CREDIT, money, cur);
            return balance(shopId);
        }
        var wallet = lockWallet(shopId);
        if (!wallet.currency().equals(cur)) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "WALLET_CURRENCY_MISMATCH", "สกุลเงินไม่ตรงกับ wallet ของร้าน");
        }
        var available = wallet.available().add(money);
        var reserved = wallet.reserved();
        try {
            insertLedger(shopId, "CREDIT", REASON_PLATFORM_HOLDING_CREDIT, money, cur, available, reserved,
                "ADMIN_CREDIT", sourceReference, key, actor, note);
        } catch (DuplicateKeyException dup) {
            var again = findLedgerByIdempotency(shopId, key);
            if (again != null) {
                assertSameLedgerPayload(again, "CREDIT", REASON_PLATFORM_HOLDING_CREDIT, money, cur);
                return balance(shopId);
            }
            throw dup;
        }
        updateWallet(shopId, available, reserved, wallet.version());
        return balance(shopId);
    }

    @Transactional
    public void reserveForWithdrawal(UUID shopId, BigDecimal amount, String currency, String withdrawalPublicId, String idempotencyKey, UUID actor) {
        var money = requirePositiveMoney(amount);
        var cur = requireCurrency(currency);
        var key = requireIdempotencyKey(idempotencyKey);
        ensureWallet(shopId);
        var existing = findLedgerByIdempotency(shopId, key);
        if (existing != null) {
            assertSameLedgerPayload(existing, "RESERVE", REASON_WITHDRAWAL_RESERVE, money.negate(), cur);
            return;
        }
        var wallet = lockWallet(shopId);
        if (!wallet.currency().equals(cur)) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "WALLET_CURRENCY_MISMATCH", "สกุลเงินไม่ตรงกับ wallet ของร้าน");
        }
        if (wallet.available().compareTo(money) < 0) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_AVAILABLE_BALANCE", "ยอดพร้อมถอนไม่พอ");
        }
        var available = wallet.available().subtract(money);
        var reserved = wallet.reserved().add(money);
        try {
            insertLedger(shopId, "RESERVE", REASON_WITHDRAWAL_RESERVE, money.negate(), cur, available, reserved,
                "WITHDRAWAL", withdrawalPublicId, key, actor, null);
        } catch (DuplicateKeyException dup) {
            var again = findLedgerByIdempotency(shopId, key);
            if (again != null) {
                assertSameLedgerPayload(again, "RESERVE", REASON_WITHDRAWAL_RESERVE, money.negate(), cur);
                return;
            }
            throw dup;
        }
        updateWallet(shopId, available, reserved, wallet.version());
    }

    @Transactional
    public void releaseReservation(UUID shopId, BigDecimal amount, String currency, String withdrawalPublicId, String idempotencyKey, UUID actor, String note) {
        var money = requirePositiveMoney(amount);
        var cur = requireCurrency(currency);
        var key = requireIdempotencyKey(idempotencyKey);
        ensureWallet(shopId);
        var existing = findLedgerByIdempotency(shopId, key);
        if (existing != null) {
            assertSameLedgerPayload(existing, "RELEASE", REASON_WITHDRAWAL_RELEASE, money, cur);
            return;
        }
        var wallet = lockWallet(shopId);
        if (wallet.reserved().compareTo(money) < 0) {
            throw new AuthException(HttpStatus.CONFLICT, "WALLET_RESERVE_MISMATCH", "ยอดกันไว้ไม่พอสำหรับคืนยอด");
        }
        var available = wallet.available().add(money);
        var reserved = wallet.reserved().subtract(money);
        try {
            insertLedger(shopId, "RELEASE", REASON_WITHDRAWAL_RELEASE, money, cur, available, reserved,
                "WITHDRAWAL", withdrawalPublicId, key, actor, note);
        } catch (DuplicateKeyException dup) {
            var again = findLedgerByIdempotency(shopId, key);
            if (again != null) {
                assertSameLedgerPayload(again, "RELEASE", REASON_WITHDRAWAL_RELEASE, money, cur);
                return;
            }
            throw dup;
        }
        updateWallet(shopId, available, reserved, wallet.version());
    }

    @Transactional
    public void capturePayout(UUID shopId, BigDecimal amount, String currency, String withdrawalPublicId, String idempotencyKey, UUID actor, String note) {
        var money = requirePositiveMoney(amount);
        var cur = requireCurrency(currency);
        var key = requireIdempotencyKey(idempotencyKey);
        ensureWallet(shopId);
        var existing = findLedgerByIdempotency(shopId, key);
        if (existing != null) {
            assertSameLedgerPayload(existing, "PAYOUT", REASON_WITHDRAWAL_PAID, money.negate(), cur);
            return;
        }
        var wallet = lockWallet(shopId);
        if (wallet.reserved().compareTo(money) < 0) {
            throw new AuthException(HttpStatus.CONFLICT, "WALLET_RESERVE_MISMATCH", "ยอดกันไว้ไม่พอสำหรับตัดจ่าย");
        }
        var available = wallet.available();
        var reserved = wallet.reserved().subtract(money);
        try {
            insertLedger(shopId, "PAYOUT", REASON_WITHDRAWAL_PAID, money.negate(), cur, available, reserved,
                "WITHDRAWAL", withdrawalPublicId, key, actor, note);
        } catch (DuplicateKeyException dup) {
            var again = findLedgerByIdempotency(shopId, key);
            if (again != null) {
                assertSameLedgerPayload(again, "PAYOUT", REASON_WITHDRAWAL_PAID, money.negate(), cur);
                return;
            }
            throw dup;
        }
        updateWallet(shopId, available, reserved, wallet.version());
    }

    private void ensureWallet(UUID shopId) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM shop_wallets WHERE shop_id = ?", Integer.class, shopId);
        if (existing != null && existing > 0) return;
        try {
            jdbc.update(
                """
                INSERT INTO shop_wallets (shop_id, currency, available, reserved, version, updated_at)
                VALUES (?, 'THB', 0, 0, 0, NOW())
                """,
                shopId
            );
        } catch (DuplicateKeyException ignored) {
            // concurrent ensure
        }
    }

    private WalletRow lockWallet(UUID shopId) {
        ensureWallet(shopId);
        var rows = jdbc.query(
            "SELECT currency, available, reserved, version FROM shop_wallets WHERE shop_id = ? FOR UPDATE",
            (rs, i) -> new WalletRow(
                rs.getString("currency"),
                money(rs.getBigDecimal("available")),
                money(rs.getBigDecimal("reserved")),
                rs.getLong("version")
            ),
            shopId
        );
        if (rows.isEmpty()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "ไม่พบ wallet ของร้าน");
        }
        return rows.get(0);
    }

    private void updateWallet(UUID shopId, BigDecimal available, BigDecimal reserved, long version) {
        int updated = jdbc.update(
            """
            UPDATE shop_wallets
               SET available = ?, reserved = ?, version = version + 1, updated_at = NOW()
             WHERE shop_id = ? AND version = ?
            """,
            available, reserved, shopId, version
        );
        if (updated != 1) {
            throw new AuthException(HttpStatus.CONFLICT, "WALLET_CONCURRENT_UPDATE", "มีการอัปเดตยอดพร้อมกัน กรุณาลองใหม่");
        }
    }

    private void insertLedger(
        UUID shopId, String entryType, String reason, BigDecimal amount, String currency,
        BigDecimal availableAfter, BigDecimal reservedAfter, String sourceType, String sourceId,
        String idempotencyKey, UUID actor, String note
    ) {
        jdbc.update(
            """
            INSERT INTO shop_wallet_ledger (
              shop_id, entry_type, reason_code, amount, currency,
              balance_available_after, balance_reserved_after, source_type, source_id,
              idempotency_key, actor_user_id, note, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """,
            shopId, entryType, reason, amount, currency, availableAfter, reservedAfter,
            sourceType, sourceId, idempotencyKey, actor, note
        );
    }

    private Map<String, Object> findLedgerByIdempotency(UUID shopId, String key) {
        var rows = jdbc.queryForList(
            """
            SELECT entry_type, reason_code, amount, currency
              FROM shop_wallet_ledger WHERE shop_id = ? AND idempotency_key = ?
            """,
            shopId, key
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static void assertSameLedgerPayload(Map<String, Object> row, String entryType, String reason, BigDecimal amount, String currency) {
        if (!entryType.equals(String.valueOf(row.get("entry_type")))
            || !reason.equals(String.valueOf(row.get("reason_code")))
            || money((BigDecimal) row.get("amount")).compareTo(money(amount)) != 0
            || !currency.equals(String.valueOf(row.get("currency")))) {
            throw new AuthException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH",
                "ใช้ idempotency key เดิมกับข้อมูลที่ต่างกัน");
        }
    }

    static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal requirePositiveMoney(BigDecimal amount) {
        if (amount == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "จำนวนเงินจำเป็น", "amount");
        }
        var money = money(amount);
        if (money.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "จำนวนเงินต้องมากกว่า 0", "amount");
        }
        if (money.scale() > 2 || money.precision() - money.scale() > 16) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "จำนวนเงินไม่ถูกต้อง", "amount");
        }
        return money;
    }

    static String requireCurrency(String currency) {
        var cur = currency == null || currency.isBlank() ? CURRENCY_THB : currency.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_THB.equals(cur)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "รองรับเฉพาะ THB", "currency");
        }
        return cur;
    }

    static String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "idempotencyKey จำเป็น (ยาวไม่เกิน 128)", "idempotencyKey");
        }
        return key.trim();
    }

    private record WalletRow(String currency, BigDecimal available, BigDecimal reserved, long version) {}
}
