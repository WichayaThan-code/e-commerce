package com.thaishopfun.platform;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** DTOs for platform support.contact setting (admin + public). */
public final class SupportContactDtos {
    private SupportContactDtos() {}

    /**
     * One weekday slot — same fields as merchant {@code ShopHour}, plus explicit {@code day}
     * (0 = Monday … 6 = Sunday) so JSON arrays stay self-describing after reordering.
     */
    public record DayHours(int day, boolean open, String start, String end) {}

    public record ChatChannel(boolean enabled, String url, List<DayHours> hours) {}
    public record LineChannel(boolean enabled, String id, String url) {}
    public record EmailChannel(boolean enabled, String address) {}
    public record PhoneChannel(boolean enabled, String number, List<DayHours> hours) {}

    /** Full admin view (all channels + enabled flags). */
    public record SupportContact(
        ChatChannel chat,
        LineChannel line,
        EmailChannel email,
        PhoneChannel phone
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicChat(String url, List<DayHours> hours) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicLine(String id, String url) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicEmail(String address) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicPhone(String number, List<DayHours> hours) {}

    /** Public payload — only enabled channels are present. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicSupportContact(
        PublicChat chat,
        PublicLine line,
        PublicEmail email,
        PublicPhone phone
    ) {}
}
