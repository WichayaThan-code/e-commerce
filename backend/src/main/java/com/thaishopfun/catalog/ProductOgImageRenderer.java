package com.thaishopfun.catalog;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.BreakIterator;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the 1200x630 Open Graph card used by product share links (Facebook / LINE / X), Shopee-style:
 * product photo on the left; shop badge, title, prices, rating and the ThaiShopFun logo on the right.
 * Fonts and logo are bundled in the jar (IBM Plex Sans Thai covers Thai + Latin), so the output does not
 * depend on fonts installed on the server.
 */
@Component
public class ProductOgImageRenderer {
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 630;
    static final String REGULAR_FONT = "fonts/IBMPlexSansThai-Regular.ttf";
    static final String BOLD_FONT = "fonts/IBMPlexSansThai-Bold.ttf";
    static final String LOGO = "og/thaishopfun-logo-name.png";

    private static final Locale THAI = Locale.forLanguageTag("th-TH");
    private static final String ELLIPSIS = "\u2026";
    private static final Color INK = new Color(0x1f2937);
    private static final Color MUTED = new Color(0x6b7280);
    private static final Color PRICE = new Color(0xee4d2d);
    private static final Color STAR = new Color(0xf59e0b);
    private static final Color STAR_EMPTY = new Color(0xe5e7eb);
    private static final Color PANEL = new Color(0xf4f5f7);
    private static final Color DIVIDER = new Color(0xeceef1);
    private static final Color BADGE_BG = new Color(0xfff1e8);
    private static final Color BADGE_INK = new Color(0xc2410c);

    private static final int PAD = 40;
    private static final int IMG_SIZE = HEIGHT - 2 * PAD; // 550 square photo
    private static final int TEXT_X = PAD + IMG_SIZE + 50; // 640
    private static final int TEXT_RIGHT = WIDTH - 50;
    private static final int TEXT_W = TEXT_RIGHT - TEXT_X;
    private static final int LOGO_W = 230;

    /** Everything the card shows; any field may be null / zero and is then simply left out. */
    public record Card(String title, String shopName, BigDecimal priceMin, BigDecimal priceMax, BigDecimal compareAtPrice,
                       BigDecimal ratingAvg, long reviewCount, Long soldCount, BufferedImage productImage) {}

    private final Font regular;
    private final Font bold;
    private final BufferedImage logo;

    public ProductOgImageRenderer() {
        ImageIO.scanForPlugins(); // registers the WebP reader (TwelveMonkeys) when running from the Boot fat jar
        this.regular = loadFont(REGULAR_FONT);
        this.bold = loadFont(BOLD_FONT);
        this.logo = loadImage(LOGO);
    }

    Font regularFont() { return regular; }

    Font boldFont() { return bold; }

    public byte[] render(Card card) {
        var canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = canvas.createGraphics();
        try {
            hints(g);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            drawPhoto(g, card.productImage());
            int logoTop = drawLogo(g);
            drawDetails(g, card, logoTop - 16);
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream(320 * 1024);
        try {
            ImageIO.write(canvas, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    // ---- left: product photo ------------------------------------------------------------------------------

    private void drawPhoto(Graphics2D g, BufferedImage photo) {
        var tile = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_ARGB);
        var t = tile.createGraphics();
        try {
            hints(t);
            t.setColor(PANEL);
            t.fillRect(0, 0, IMG_SIZE, IMG_SIZE);
            if (photo != null && photo.getWidth() > 0 && photo.getHeight() > 0) {
                double ratio = (double) photo.getWidth() / photo.getHeight();
                boolean nearSquare = ratio > 0.75 && ratio < 1.33;
                // near-square photos fill the tile (cover); very wide / tall ones are fitted whole (contain)
                double scale = nearSquare
                    ? Math.max((double) IMG_SIZE / photo.getWidth(), (double) IMG_SIZE / photo.getHeight())
                    : Math.min((double) IMG_SIZE / photo.getWidth(), (double) IMG_SIZE / photo.getHeight());
                int w = Math.max(1, (int) Math.round(photo.getWidth() * scale));
                int h = Math.max(1, (int) Math.round(photo.getHeight() * scale));
                if (!nearSquare) {
                    t.setColor(Color.WHITE);
                    t.fillRect(0, 0, IMG_SIZE, IMG_SIZE);
                }
                t.drawImage(scaled(photo, w, h), (IMG_SIZE - w) / 2, (IMG_SIZE - h) / 2, null);
            } else if (logo != null) {
                int w = 340;
                int h = (int) Math.round(logo.getHeight() * (w / (double) logo.getWidth()));
                t.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                t.drawImage(scaled(logo, w, h), (IMG_SIZE - w) / 2, (IMG_SIZE - h) / 2, null);
            }
            // anti-aliased rounded corners: keep only the pixels inside the rounded rect
            t.setComposite(AlphaComposite.DstIn);
            var mask = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_ARGB);
            var m = mask.createGraphics();
            hints(m);
            m.setColor(Color.BLACK);
            m.fill(new RoundRectangle2D.Float(0, 0, IMG_SIZE, IMG_SIZE, 36, 36));
            m.dispose();
            t.drawImage(mask, 0, 0, null);
        } finally {
            t.dispose();
        }
        g.drawImage(tile, PAD, PAD, null);
        g.setColor(DIVIDER);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Float(PAD, PAD, IMG_SIZE, IMG_SIZE, 36, 36));
    }

    /** Progressive halving keeps big photos sharp instead of aliased when shrunk a lot. */
    private static BufferedImage scaled(BufferedImage src, int targetW, int targetH) {
        BufferedImage current = toArgb(src);
        int w = current.getWidth();
        int h = current.getHeight();
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2;
            h /= 2;
            current = resize(current, w, h);
        }
        return (w == targetW && h == targetH) ? current : resize(current, targetW, targetH);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        var dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        var dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    // ---- right: text --------------------------------------------------------------------------------------

    private void drawDetails(Graphics2D g, Card card, int bottomLimit) {
        boolean range = card.priceMin() != null && card.priceMax() != null && card.priceMax().compareTo(card.priceMin()) > 0;
        BigDecimal reference = range ? card.priceMax() : card.priceMin();
        boolean strike = card.compareAtPrice() != null && reference != null && card.compareAtPrice().compareTo(reference) > 0;
        boolean ratingRow = card.reviewCount() > 0 || (card.soldCount() != null && card.soldCount() > 0);
        boolean badgeRow = notBlank(card.shopName());

        int badgeH = badgeRow ? 42 + 20 : 0;
        int below = (strike ? 50 : 0) + (card.priceMin() != null ? 92 : 0) + (ratingRow ? 44 : 0);
        int lineH = 56;
        int top = PAD + 22;
        int maxLines = Math.max(1, Math.min(3, (bottomLimit - top - badgeH - below - 24) / lineH));
        g.setFont(bold.deriveFont(44f));
        var titleFm = g.getFontMetrics();
        var lines = wrap(clean(card.title()), titleFm, TEXT_W, maxLines);

        // short content sits a little above the middle of the free area instead of hugging the top edge
        int contentH = badgeH + lines.size() * lineH + 24 + below;
        int free = bottomLimit - top - contentH;
        int y = top + Math.max(0, free / 3);

        if (badgeRow) {
            g.setFont(bold.deriveFont(22f));
            var fm = g.getFontMetrics();
            var name = clean(card.shopName());
            var label = ellipsize(name.startsWith("\u0E23\u0E49\u0E32\u0E19") ? name : "\u0E23\u0E49\u0E32\u0E19 " + name, fm, TEXT_W - 40); // "ร้าน"
            int h = 42;
            int w = fm.stringWidth(label) + 40;
            g.setColor(BADGE_BG);
            g.fill(new RoundRectangle2D.Float(TEXT_X, y, w, h, h, h));
            g.setColor(BADGE_INK);
            g.drawString(label, TEXT_X + 20, centeredBaseline(fm, y, h));
            y += badgeH;
        }

        g.setFont(bold.deriveFont(44f));
        g.setColor(INK);
        int baseline = y + 46;
        for (var line : lines) {
            g.drawString(line, TEXT_X, baseline);
            baseline += lineH;
        }
        y = baseline - lineH + 24;
        FontMetrics fm;

        if (strike) {
            g.setFont(regular.deriveFont(30f));
            fm = g.getFontMetrics();
            var text = money(card.compareAtPrice());
            int base = y + 34;
            g.setColor(new Color(0x9ca3af));
            g.drawString(text, TEXT_X, base);
            int w = fm.stringWidth(text);
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Line2D.Float(TEXT_X - 2, base - 10, TEXT_X + w + 2, base - 10));
            if (!range && card.priceMin() != null && card.compareAtPrice().signum() > 0) {
                int pct = card.compareAtPrice().subtract(card.priceMin()).multiply(BigDecimal.valueOf(100))
                    .divide(card.compareAtPrice(), 0, RoundingMode.HALF_UP).intValue();
                if (pct > 0) {
                    g.setFont(bold.deriveFont(22f));
                    var bfm = g.getFontMetrics();
                    var badge = "-" + pct + "%";
                    int bw = bfm.stringWidth(badge) + 24;
                    int bh = 34;
                    int bx = TEXT_X + w + 18;
                    int by = base - 27;
                    g.setColor(PRICE);
                    g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 10, 10));
                    g.setColor(Color.WHITE);
                    g.drawString(badge, bx + 12, centeredBaseline(bfm, by, bh));
                }
            }
            y += 50;
        }

        if (card.priceMin() != null) {
            var text = range ? money(card.priceMin()) + " - " + money(card.priceMax()) : money(card.priceMin());
            float size = 64f;
            g.setFont(bold.deriveFont(size));
            while (g.getFontMetrics().stringWidth(text) > TEXT_W && size > 36f) {
                size -= 2f;
                g.setFont(bold.deriveFont(size));
            }
            g.setColor(PRICE);
            g.drawString(text, TEXT_X, y + 66);
            y += 92;
        }

        if (ratingRow) {
            int x = TEXT_X;
            int rowH = 34;
            g.setFont(regular.deriveFont(24f));
            fm = g.getFontMetrics();
            int base = centeredBaseline(fm, y, rowH);
            if (card.reviewCount() > 0) {
                double rating = card.ratingAvg() == null ? 0 : Math.max(0, Math.min(5, card.ratingAvg().doubleValue()));
                for (int i = 0; i < 5; i++) {
                    drawStar(g, x + 15, y + rowH / 2.0, 15, Math.max(0, Math.min(1, rating - i)));
                    x += 34;
                }
                x += 8;
                g.setColor(INK);
                g.setFont(bold.deriveFont(24f));
                var avg = card.ratingAvg() == null ? "" : card.ratingAvg().setScale(1, RoundingMode.HALF_UP).toPlainString();
                g.drawString(avg, x, base);
                x += g.getFontMetrics().stringWidth(avg) + 10;
                g.setFont(regular.deriveFont(24f));
                g.setColor(MUTED);
                var count = "(" + (card.reviewCount() >= 10_000 ? compact(card.reviewCount()) : count(card.reviewCount())) + " \u0E23\u0E35\u0E27\u0E34\u0E27)"; // "(n รีวิว)"
                g.drawString(count, x, base);
                x += fm.stringWidth(count);
            }
            if (card.soldCount() != null && card.soldCount() > 0) {
                var sold = (x > TEXT_X ? "  \u00B7  " : "") + "\u0E02\u0E32\u0E22\u0E41\u0E25\u0E49\u0E27 " + compact(card.soldCount()); // "ขายแล้ว n"
                var full = sold + " \u0E0A\u0E34\u0E49\u0E19"; // " ชิ้น"
                g.setColor(MUTED);
                if (fm.stringWidth(full) <= TEXT_RIGHT - x) g.drawString(full, x, base);
                else if (fm.stringWidth(sold) <= TEXT_RIGHT - x) g.drawString(sold, x, base);
            }
        }
    }

    private int drawLogo(Graphics2D g) {
        int bottom = HEIGHT - PAD - 6;
        if (logo == null) {
            g.setFont(bold.deriveFont(34f));
            var fm = g.getFontMetrics();
            int x = TEXT_RIGHT - fm.stringWidth("ThaiShopFun");
            g.setColor(new Color(0x13306b));
            g.drawString("ThaiShopFun", x, bottom - fm.getDescent());
            return bottom - fm.getHeight();
        }
        int h = (int) Math.round(logo.getHeight() * (LOGO_W / (double) logo.getWidth()));
        int top = bottom - h;
        g.drawImage(scaled(logo, LOGO_W, h), TEXT_RIGHT - LOGO_W, top, null);
        return top;
    }

    private static void drawStar(Graphics2D g, double cx, double cy, double r, double fill) {
        Shape star = star(cx, cy, r);
        g.setColor(STAR_EMPTY);
        g.fill(star);
        if (fill > 0) {
            var clip = g.getClip();
            g.clip(new Rectangle2D.Double(cx - r, cy - r, 2 * r * fill, 2 * r));
            g.setColor(STAR);
            g.fill(star);
            g.setClip(clip);
        }
    }

    private static Shape star(double cx, double cy, double r) {
        var p = new Path2D.Double();
        double inner = r * 0.48;
        for (int i = 0; i < 10; i++) {
            double radius = i % 2 == 0 ? r : inner;
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double px = cx + radius * Math.cos(angle);
            double py = cy + 1.5 + radius * Math.sin(angle);
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.closePath();
        return p;
    }

    // ---- text helpers -------------------------------------------------------------------------------------

    /**
     * Thai has no spaces between words, so lines break with the Thai dictionary BreakIterator (falls back to
     * grapheme clusters for a single over-long word, never splitting a consonant from its vowel / tone marks).
     * The last allowed line gets an ellipsis when text remains.
     */
    static List<String> wrap(String text, FontMetrics fm, int maxWidth, int maxLines) {
        var lines = new ArrayList<String>();
        if (text == null || text.isBlank()) return lines;
        var words = BreakIterator.getLineInstance(THAI);
        words.setText(text);
        int start = 0;
        int len = text.length();
        while (start < len && lines.size() < maxLines) {
            int end = start;
            int next = words.following(start);
            while (next != BreakIterator.DONE && fm.stringWidth(text.substring(start, next).stripTrailing()) <= maxWidth) {
                end = next;
                next = words.next();
            }
            if (end == start) end = fitGraphemes(text, start, fm, maxWidth);
            if (end < len && !Character.isWhitespace(text.charAt(end)) && !Character.isWhitespace(text.charAt(end - 1))) {
                // the dictionary split a word (e.g. a transliterated name); break at the last space if the line stays >= 60% full
                int space = text.lastIndexOf(' ', end - 1);
                if (space > start && fm.stringWidth(text.substring(start, space)) >= maxWidth * 0.6) end = space + 1;
            }
            lines.add(text.substring(start, end).strip());
            start = end;
            while (start < len && Character.isWhitespace(text.charAt(start))) start++;
        }
        if (start < len && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, ellipsize(lines.get(last) + ELLIPSIS, fm, maxWidth, true));
        }
        return lines;
    }

    private static int fitGraphemes(String text, int start, FontMetrics fm, int maxWidth) {
        var chars = BreakIterator.getCharacterInstance(THAI);
        chars.setText(text);
        int end = chars.following(start);
        int next = chars.next();
        while (next != BreakIterator.DONE && fm.stringWidth(text.substring(start, next)) <= maxWidth) {
            end = next;
            next = chars.next();
        }
        return end;
    }

    static String ellipsize(String text, FontMetrics fm, int maxWidth) {
        return ellipsize(text, fm, maxWidth, false);
    }

    private static String ellipsize(String text, FontMetrics fm, int maxWidth, boolean forced) {
        if (!forced && fm.stringWidth(text) <= maxWidth) return text;
        var body = text.endsWith(ELLIPSIS) ? text.substring(0, text.length() - 1) : text;
        if (forced && fm.stringWidth(body + ELLIPSIS) <= maxWidth) return body.stripTrailing() + ELLIPSIS;
        var chars = BreakIterator.getCharacterInstance(THAI);
        chars.setText(body);
        int end = chars.last();
        while (end > 0 && fm.stringWidth(body.substring(0, end).stripTrailing() + ELLIPSIS) > maxWidth) {
            end = chars.previous();
        }
        return body.substring(0, Math.max(0, end)).stripTrailing() + ELLIPSIS;
    }

    private static int centeredBaseline(FontMetrics fm, int top, int height) {
        // centre on the Latin/Thai body (cap height ~ 0.7em) rather than the full ascent incl. upper Thai marks
        return top + (int) Math.round(height / 2.0 + fm.getFont().getSize2D() * 0.36);
    }

    static String money(BigDecimal value) {
        var symbols = DecimalFormatSymbols.getInstance(Locale.US);
        var format = new DecimalFormat(value.stripTrailingZeros().scale() > 0 ? "#,##0.00" : "#,##0", symbols);
        return "\u0E3F" + format.format(value);
    }

    private static String count(long n) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)).format(n);
    }

    /** Thai marketplace style: 950, 5.6พัน, 1.2หมื่น, 3.4แสน, 1.1ล้าน. */
    static String compact(long n) {
        if (n < 1_000) return Long.toString(n);
        String[] units = {"\u0E1E\u0E31\u0E19", "\u0E2B\u0E21\u0E37\u0E48\u0E19", "\u0E41\u0E2A\u0E19", "\u0E25\u0E49\u0E32\u0E19"};
        long[] sizes = {1_000L, 10_000L, 100_000L, 1_000_000L};
        int i = n >= 1_000_000L ? 3 : n >= 100_000L ? 2 : n >= 10_000L ? 1 : 0;
        var value = BigDecimal.valueOf(n).divide(BigDecimal.valueOf(sizes[i]), 1, RoundingMode.DOWN).stripTrailingZeros();
        return value.toPlainString() + units[i];
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static Font loadFont(String path) {
        try (InputStream in = resource(path)) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Cannot load bundled font " + path, e);
        }
    }

    private static BufferedImage loadImage(String path) {
        try (InputStream in = resource(path)) {
            return ImageIO.read(in);
        } catch (IOException | IllegalStateException e) {
            return null; // card falls back to a text wordmark
        }
    }

    private static InputStream resource(String path) throws IOException {
        var in = ProductOgImageRenderer.class.getClassLoader().getResourceAsStream(path);
        if (in == null) throw new IOException("missing classpath resource " + path);
        return in;
    }
}
