package com.thaishopfun.content;

import com.thaishopfun.auth.AuthException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sanitizes article body HTML from the TipTap editor. Legacy markdown (no HTML tags) is kept as-is.
 */
public final class ArticleHtml {
    public static final int MAX_LENGTH = 200_000;

    private static final Set<String> SIZE_CLASSES = Set.of(
        "hc-size-sm", "hc-size-md", "hc-size-lg", "hc-size-heading");
    private static final Pattern LOOKS_LIKE_HTML = Pattern.compile("<[a-zA-Z!/?]");
    private static final Pattern ARTICLE_IMAGE = Pattern.compile("^/api/article-images/[0-9a-fA-F-]{36}$");
    private static final Pattern ALIGN_STYLE = Pattern.compile(
        "^\\s*text-align\\s*:\\s*(left|center)\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESIDUE = Pattern.compile("(?i)javascript:|<\\s*script|\\son[a-z]+\\s*=");

    private static final Safelist SAFELIST = Safelist.none()
        .addTags("p", "br", "strong", "b", "em", "i", "ul", "ol", "li", "h2", "h3", "img", "span")
        .addAttributes("p", "style", "class")
        .addAttributes("h2", "style", "class")
        .addAttributes("h3", "style", "class")
        .addAttributes("span", "class")
        .addAttributes("li", "class")
        .addAttributes("img", "src", "alt", "width")
        .preserveRelativeLinks(true);

    private ArticleHtml() {}

    public static boolean looksLikeHtml(String value) {
        return value != null && LOOKS_LIKE_HTML.matcher(value).find();
    }

    public static boolean isBlank(String value) {
        if (value == null || value.isBlank()) return true;
        if (!looksLikeHtml(value)) return value.isBlank();
        var doc = Jsoup.parseBodyFragment(value);
        if (!doc.select("img[src]").isEmpty()) return false;
        return doc.text().isBlank();
    }

    /** Required body: sanitized HTML or legacy markdown/plain text. */
    public static String required(String raw, String field) {
        String cleaned = clean(raw, field);
        if (cleaned == null || isBlank(cleaned)) {
            throw bad("ARTICLE_FIELD_REQUIRED", field + " is required", field);
        }
        return cleaned;
    }

    /** Optional localized body. */
    public static String optional(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = clean(raw, field);
        return isBlank(cleaned) ? null : cleaned;
    }

    public static String plainText(String value) {
        if (value == null || value.isBlank()) return "";
        if (!looksLikeHtml(value)) return value.trim();
        return Jsoup.parseBodyFragment(value).text().trim();
    }

    private static String clean(String raw, String field) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_LENGTH) {
            throw bad("ARTICLE_FIELD_TOO_LONG", field + " must be at most " + MAX_LENGTH + " characters", field);
        }
        if (!looksLikeHtml(trimmed)) {
            return trimmed;
        }
        var cleaned = Jsoup.clean(
            trimmed,
            "https://thaishopfun.invalid/",
            SAFELIST,
            new Document.OutputSettings().prettyPrint(false).charset(StandardCharsets.UTF_8).escapeMode(Entities.EscapeMode.base)
        );
        cleaned = normalize(cleaned);
        if (RESIDUE.matcher(cleaned).find()) {
            throw bad("ARTICLE_INVALID_HTML", field + " contains disallowed markup", field);
        }
        if (cleaned.length() > MAX_LENGTH) {
            throw bad("ARTICLE_FIELD_TOO_LONG", field + " must be at most " + MAX_LENGTH + " characters", field);
        }
        return cleaned;
    }

    private static String normalize(String html) {
        var doc = Jsoup.parseBodyFragment(html);
        for (Element el : doc.body().getAllElements()) {
            if ("b".equals(el.tagName())) el.tagName("strong");
            if ("i".equals(el.tagName())) el.tagName("em");
            cleanClass(el);
            cleanStyle(el);
            if ("img".equals(el.tagName())) cleanImage(el);
        }
        return doc.body().html();
    }

    private static void cleanClass(Element el) {
        if (!el.hasAttr("class")) return;
        String kept = null;
        for (String token : el.className().trim().split("\\s+")) {
            if (SIZE_CLASSES.contains(token)) {
                kept = token;
                break;
            }
        }
        if (kept == null) el.removeAttr("class");
        else el.attr("class", kept);
    }

    private static void cleanStyle(Element el) {
        if (!el.hasAttr("style")) return;
        String style = el.attr("style").trim();
        var match = ALIGN_STYLE.matcher(style);
        if (match.matches()) {
            el.attr("style", "text-align: " + match.group(1).toLowerCase(Locale.ROOT));
        } else {
            el.removeAttr("style");
        }
    }

    private static void cleanImage(Element el) {
        String src = el.attr("src").trim();
        if (!ARTICLE_IMAGE.matcher(src).matches()) {
            el.remove();
            return;
        }
        el.attr("src", src);
        if (el.hasAttr("alt") && el.attr("alt").length() > 200) {
            el.attr("alt", el.attr("alt").substring(0, 200));
        }
        Integer widthPct = parseWidthPercent(el.attr("width"));
        for (Attribute attr : el.attributes().asList()) {
            String key = attr.getKey();
            if (!"src".equals(key) && !"alt".equals(key) && !"width".equals(key)) el.removeAttr(key);
        }
        if (widthPct == null || widthPct == 100) el.removeAttr("width");
        else el.attr("width", String.valueOf(widthPct));
    }

    static Integer parseWidthPercent(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.endsWith("%")) trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 10 || value > 100) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static AuthException bad(String code, String message, String field) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message, field);
    }
}
