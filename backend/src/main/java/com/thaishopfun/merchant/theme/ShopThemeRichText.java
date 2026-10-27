package com.thaishopfun.merchant.theme;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * AG4 RICH_TEXT: a deliberately tiny markdown subset (h2-h3, bold, italic, ordered / unordered lists, links,
 * blockquote) rendered to HTML on save and then cleaned with a jsoup {@link Safelist}. Merchant markdown is never
 * trusted: raw HTML, images, {@code javascript:} / {@code on*=} patterns and any link that is not https or an internal
 * path make {@link #render(String)} return {@code null} (the caller answers 400 THEME_RICHTEXT_INVALID).
 *
 * <p>The storefront renders the {@code markdown} source with react-markdown + rehype-sanitize (no
 * dangerouslySetInnerHTML); the sanitized {@code html} stored next to it is the backend-verified preview / fallback.
 */
final class ShopThemeRichText {
    static final int MAX_LENGTH = 5000;

    private static final Pattern INTERNAL_PATH = Pattern.compile("^/(?!/)[\\w\\-./?#=&%~+:@,]*$");
    private static final Pattern HTTPS = Pattern.compile("^https://[\\w\\-./?#=&%~+:@,]+$", Pattern.CASE_INSENSITIVE);
    /** Raw HTML (any tag, comment, doctype, processing instruction) is not part of the allowed subset. */
    private static final Pattern RAW_HTML = Pattern.compile("<[a-zA-Z!/?]");
    private static final Pattern SCRIPT_SCHEME = Pattern.compile("(?i)(j\\s*a\\s*v\\s*a\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t|v\\s*b\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t)\\s*:");
    private static final Pattern DATA_SCHEME = Pattern.compile("(?i)\\]\\(\\s*data\\s*:");
    private static final Pattern EVENT_HANDLER = Pattern.compile("(?i)(^|[^a-z0-9])on[a-z]{3,20}\\s*=");
    private static final Pattern IMAGE = Pattern.compile("!\\[");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]{1,200})]\\(([^)\\s]{0,500})\\)");
    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern BULLET = Pattern.compile("^[-*+]\\s+(.+)$");
    private static final Pattern ORDERED = Pattern.compile("^\\d{1,3}[.)]\\s+(.+)$");
    private static final Pattern QUOTE = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)");
    private static final Pattern RESIDUE = Pattern.compile("(?i)javascript:|<\\s*script|\\son[a-z]+\\s*=");

    private static final Safelist SAFELIST = Safelist.none()
        .addTags("h2", "h3", "p", "strong", "em", "ul", "ol", "li", "blockquote", "a")
        .addAttributes("a", "href")
        .addProtocols("a", "href", "https")
        .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer")
        .preserveRelativeLinks(true);

    private ShopThemeRichText() {}

    private static final class Invalid extends RuntimeException {
        Invalid() {
            super(null, null, false, false);
        }
    }

    static boolean isSafeLink(String url) {
        return url != null && (INTERNAL_PATH.matcher(url).matches() || HTTPS.matcher(url).matches());
    }

    /** Sanitized HTML for the markdown, or {@code null} when it is over-long or contains anything outside the subset. */
    static String render(String markdown) {
        if (markdown == null) return "";
        if (markdown.length() > MAX_LENGTH) return null;
        var source = markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (RAW_HTML.matcher(source).find() || SCRIPT_SCHEME.matcher(source).find()
            || DATA_SCHEME.matcher(source).find() || EVENT_HANDLER.matcher(source).find()
            || IMAGE.matcher(source).find()) {
            return null;
        }
        String html;
        try {
            html = toHtml(source);
        } catch (Invalid invalid) {
            return null;
        }
        // The base URI lets jsoup resolve internal paths (/shop/x) so they pass the https-only protocol check while the
        // original relative href is preserved; protocol-relative and javascript: targets were rejected above.
        var cleaned = Jsoup.clean(
            html, "https://thaishopfun.invalid/", SAFELIST,
            new Document.OutputSettings().prettyPrint(false).charset(StandardCharsets.UTF_8).escapeMode(Entities.EscapeMode.base)
        );
        if (RESIDUE.matcher(cleaned).find()) return null;
        return cleaned;
    }

    private static String toHtml(String source) {
        var out = new StringBuilder();
        var paragraph = new StringBuilder();
        var quote = new StringBuilder();
        String list = null; // "ul" | "ol" | null

        for (var rawLine : source.split("\n", -1)) {
            var line = rawLine.strip();
            var heading = HEADING.matcher(line);
            var quoted = QUOTE.matcher(line);
            var bullet = BULLET.matcher(line);
            var ordered = ORDERED.matcher(line);

            if (line.isEmpty()) {
                flushParagraph(out, paragraph);
                flushQuote(out, quote);
                list = closeList(out, list);
            } else if (heading.matches()) {
                flushParagraph(out, paragraph);
                flushQuote(out, quote);
                list = closeList(out, list);
                var tag = heading.group(1).length() >= 3 ? "h3" : "h2";
                out.append('<').append(tag).append('>').append(inline(heading.group(2))).append("</").append(tag).append('>');
            } else if (quoted.matches()) {
                flushParagraph(out, paragraph);
                list = closeList(out, list);
                if (quote.length() > 0) quote.append(' ');
                quote.append(quoted.group(1).strip());
            } else if (bullet.matches() || ordered.matches()) {
                flushParagraph(out, paragraph);
                flushQuote(out, quote);
                var isOrdered = ordered.matches();
                var tag = isOrdered ? "ol" : "ul";
                var item = isOrdered ? ordered : bullet;
                if (!tag.equals(list)) {
                    list = closeList(out, list);
                    out.append('<').append(tag).append('>');
                    list = tag;
                }
                out.append("<li>").append(inline(item.group(1))).append("</li>");
            } else {
                flushQuote(out, quote);
                list = closeList(out, list);
                if (paragraph.length() > 0) paragraph.append(' ');
                paragraph.append(line);
            }
        }
        flushParagraph(out, paragraph);
        flushQuote(out, quote);
        closeList(out, list);
        return out.toString();
    }

    private static void flushParagraph(StringBuilder out, StringBuilder paragraph) {
        if (paragraph.length() == 0) return;
        out.append("<p>").append(inline(paragraph.toString())).append("</p>");
        paragraph.setLength(0);
    }

    private static void flushQuote(StringBuilder out, StringBuilder quote) {
        if (quote.length() == 0) return;
        out.append("<blockquote><p>").append(inline(quote.toString())).append("</p></blockquote>");
        quote.setLength(0);
    }

    private static String closeList(StringBuilder out, String list) {
        if (list != null) out.append("</").append(list).append('>');
        return null;
    }

    private static String inline(String raw) {
        var out = new StringBuilder();
        var matcher = LINK.matcher(raw);
        int last = 0;
        while (matcher.find()) {
            out.append(emphasis(escape(raw.substring(last, matcher.start()))));
            var url = matcher.group(2);
            if (!isSafeLink(url)) throw new Invalid();
            out.append("<a href=\"").append(escape(url)).append("\">").append(emphasis(escape(matcher.group(1)))).append("</a>");
            last = matcher.end();
        }
        out.append(emphasis(escape(raw.substring(last))));
        return out.toString();
    }

    private static String emphasis(String escaped) {
        var bold = BOLD.matcher(escaped).replaceAll("<strong>$1</strong>");
        return ITALIC.matcher(bold).replaceAll("<em>$1</em>");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
