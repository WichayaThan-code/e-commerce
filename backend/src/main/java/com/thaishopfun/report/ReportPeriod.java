package com.thaishopfun.report;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Report period, parsed exactly like the merchant dashboard: Asia/Bangkok calendar days, inclusive
 * [from 00:00, to + 1 day 00:00), range = today | 7d | 30d | 1y (ending today) or from/to (both required, max 3 years).
 * Default (nothing given) = today. The previous period is the same-length window right before {@code from}.
 */
public record ReportPeriod(String range, LocalDate from, LocalDate to, int days) {
    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    public static final int MAX_YEARS = 3;

    public static ReportPeriod parse(Clock clock, String rawRange, String rawFrom, String rawTo) {
        LocalDate today = LocalDate.now(clock.withZone(BANGKOK));
        String range;
        LocalDate from;
        LocalDate to;
        if (notBlank(rawFrom) || notBlank(rawTo)) {
            if (!notBlank(rawFrom) || !notBlank(rawTo)) throw invalid("from", "REQUIRED", "from and to are both required");
            from = date(rawFrom, "from");
            to = date(rawTo, "to");
            if (from.isAfter(to)) throw invalid("from", "RANGE", "from must not be after to");
            if (from.isBefore(to.minusYears(MAX_YEARS))) throw invalid("from", "RANGE", "range must not exceed " + MAX_YEARS + " years");
            range = "custom";
        } else if (notBlank(rawRange)) {
            range = rawRange.trim().toLowerCase();
            to = today;
            from = switch (range) {
                case "today" -> today;
                case "7d" -> today.minusDays(6);
                case "30d" -> today.minusDays(29);
                case "1y" -> today.minusYears(1).plusDays(1);
                default -> throw invalid("range", "INVALID", "range must be today, 7d, 30d or 1y");
            };
        } else {
            from = to = today;
            range = "today";
        }
        return new ReportPeriod(range, from, to, (int) ChronoUnit.DAYS.between(from, to) + 1);
    }

    public Timestamp start() { return Timestamp.from(from.atStartOfDay(BANGKOK).toInstant()); }

    /** Exclusive end instant: midnight after {@code to}. */
    public Timestamp end() { return Timestamp.from(to.plusDays(1).atStartOfDay(BANGKOK).toInstant()); }

    public ReportPeriod previous() {
        LocalDate prevTo = from.minusDays(1);
        return new ReportPeriod("previous", prevTo.minusDays(days - 1L), prevTo, days);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static ValidationException invalid(String field, String code, String message) {
        return new ValidationException(List.of(new FieldViolation(field, code, message)));
    }

    private static LocalDate date(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw invalid(field, "INVALID", field + " must be YYYY-MM-DD");
        }
    }
}
