package com.thaishopfun.admin;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;

/**
 * Allowed client-supplied reason codes for platform suspend / restore.
 * Must stay in sync with frontend {@code SUSPEND_REASON_CODES} in ReasonDialog.tsx.
 * System-only codes ({@link #MERCHANT_SUSPENDED}, {@link #LEGACY_NO_REASON}, …) must never be accepted from the API.
 */
public final class SuspensionReasonCodes {
    private SuspensionReasonCodes() {}

    public static final String ADMIN_SUSPEND = "ADMIN_SUSPEND";
    public static final String POLICY_VIOLATION = "POLICY_VIOLATION";
    public static final String FRAUD = "FRAUD";
    public static final String ABUSE = "ABUSE";
    public static final String OTHER = "OTHER";

    public static final String ADMIN_RESTORE = "ADMIN_RESTORE";

    /** Cascade shop suspend when the merchant is suspended — system only. */
    public static final String MERCHANT_SUSPENDED = "MERCHANT_SUSPENDED";
    /** Historical audit rows only — never accept from new API writes. */
    public static final String LEGACY_NO_REASON = "NO_REASON_GIVEN";
    /** Historical user-status audit rows (pre-allowlist). */
    public static final String ADMIN_STATUS = "ADMIN_STATUS";

    public static final List<String> SUSPEND_ALLOWED = List.of(
        ADMIN_SUSPEND, POLICY_VIOLATION, FRAUD, ABUSE, OTHER);
    public static final List<String> RESTORE_ALLOWED = List.of(ADMIN_RESTORE, OTHER);

    private static final Set<String> SUSPEND_SET = Set.copyOf(SUSPEND_ALLOWED);
    private static final Set<String> RESTORE_SET = Set.copyOf(RESTORE_ALLOWED);

    /** Suspend: blank → 400 REASON_CODE_REQUIRED; unknown/reserved → 400 REASON_CODE_INVALID. */
    public static String requireSuspend(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REASON_CODE_REQUIRED", "กรุณาเลือกเหตุผลการระงับ");
        }
        return requireIn(reasonCode.trim(), SUSPEND_SET);
    }

    /** Suspend when the API already defaults a blank code (shop/merchant ReasonRequest). */
    public static String resolveSuspend(String reasonCode, String fallback) {
        if (reasonCode == null || reasonCode.isBlank()) return fallback;
        return requireIn(reasonCode.trim(), SUSPEND_SET);
    }

    /** Restore: blank → fallback; otherwise must be in the restore allowlist. */
    public static String resolveRestore(String reasonCode, String fallback) {
        if (reasonCode == null || reasonCode.isBlank()) return fallback;
        return requireIn(reasonCode.trim(), RESTORE_SET);
    }

    private static String requireIn(String code, Set<String> allowed) {
        if (!allowed.contains(code)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REASON_CODE_INVALID", "รหัสเหตุผลไม่ถูกต้อง");
        }
        return code;
    }
}
