package com.thaishopfun.catalog;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductOgImageRendererTest {
    private static final String THAI_TITLE = "\u0E1E\u0E27\u0E07\u0E01\u0E38\u0E0D\u0E41\u0E08 Clicker \u0E25\u0E32\u0E22\u0E40\u0E17\u0E04\u0E27\u0E31\u0E19\u0E42\u0E14"; // พวงกุญแจ Clicker ลายเทควันโด
    private static final Path PREVIEW_DIR = Path.of("target", "og-preview");

    private final ProductOgImageRenderer renderer = new ProductOgImageRenderer();

    @Test
    void rendersThaiProductCardAs1200x630Png() throws Exception {
        var card = new ProductOgImageRenderer.Card(THAI_TITLE, "KickLab Studio", new BigDecimal("99"), new BigDecimal("99"),
            new BigDecimal("180"), new BigDecimal("4.8"), 12, 35L, samplePhoto());

        byte[] png = renderer.render(card);
        var image = ImageIO.read(new ByteArrayInputStream(png));

        assertNotNull(image, "output must be a readable PNG");
        assertEquals(ProductOgImageRenderer.WIDTH, image.getWidth());
        assertEquals(ProductOgImageRenderer.HEIGHT, image.getHeight());
        assertTrue(png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G', "PNG signature");
        assertTrue(nonWhitePixels(image, 0, 600) > 50_000, "left half shows the product photo");
        assertTrue(nonWhitePixels(image, 600, 1200) > 5_000, "right half shows title / price / rating / logo");
        assertTrue(pixelsNear(image, 600, 1200, new Color(0xee4d2d)) > 500, "price is drawn in the accent colour");

        Files.createDirectories(PREVIEW_DIR);
        Files.write(PREVIEW_DIR.resolve("product-og-card.png"), png);
    }

    @Test
    void bundledFontsCoverThaiAndLatinSoNothingRendersAsBoxes() {
        var text = THAI_TITLE + " \u0E3F1,299.50 - \u0E3F180 (12 \u0E23\u0E35\u0E27\u0E34\u0E27) \u0E02\u0E32\u0E22\u0E41\u0E25\u0E49\u0E27 35 \u0E0A\u0E34\u0E49\u0E19 \u0E23\u0E49\u0E32\u0E19 \u2026 \u00B7 -45%";
        assertEquals(-1, renderer.boldFont().canDisplayUpTo(text), "bold font is missing a glyph");
        assertEquals(-1, renderer.regularFont().canDisplayUpTo(text), "regular font is missing a glyph");
    }

    @Test
    void longTitleWrapsToAtMostThreeLinesWithEllipsisAndPlaceholderWithoutPhoto() throws Exception {
        var longTitle = THAI_TITLE.repeat(6);
        var card = new ProductOgImageRenderer.Card(longTitle, null, new BigDecimal("199"), new BigDecimal("1299.50"),
            null, null, 0, null, null);
        var image = ImageIO.read(new ByteArrayInputStream(renderer.render(card)));
        assertEquals(1200, image.getWidth());
        assertEquals(630, image.getHeight());
        assertTrue(nonWhitePixels(image, 0, 600) > 50_000, "placeholder panel is drawn when there is no photo");

        var g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        g.setFont(renderer.boldFont().deriveFont(44f));
        var lines = ProductOgImageRenderer.wrap(longTitle, g.getFontMetrics(), 510, 3);
        g.dispose();
        assertEquals(3, lines.size());
        assertTrue(lines.get(2).endsWith("\u2026"));
        assertTrue(lines.stream().allMatch(line -> !line.isBlank()));
    }

    @Test
    void formatsThaiBahtPrices() {
        assertEquals("\u0E3F99", ProductOgImageRenderer.money(new BigDecimal("99.00")));
        assertEquals("\u0E3F1,299.50", ProductOgImageRenderer.money(new BigDecimal("1299.5")));
        assertEquals("5.6\u0E1E\u0E31\u0E19", ProductOgImageRenderer.compact(5678));
    }

    @Test
    void webpReaderIsAvailableForStoredProductImages() {
        assertTrue(ImageIO.getImageReadersByFormatName("webp").hasNext(), "imageio-webp must be on the classpath");
    }

    private static BufferedImage samplePhoto() {
        var photo = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);
        var g = photo.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x1d4ed8), 800, 800, new Color(0xf97316)));
        g.fillRect(0, 0, 800, 800);
        g.setColor(Color.WHITE);
        g.fillOval(250, 250, 300, 300);
        g.dispose();
        return photo;
    }

    private static int nonWhitePixels(BufferedImage image, int fromX, int toX) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = fromX; x < toX; x++) {
                int rgb = image.getRGB(x, y) & 0xffffff;
                if (rgb != 0xffffff) count++;
            }
        }
        return count;
    }

    private static int pixelsNear(BufferedImage image, int fromX, int toX, Color color) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = fromX; x < toX; x++) {
                var c = new Color(image.getRGB(x, y));
                if (Math.abs(c.getRed() - color.getRed()) < 12 && Math.abs(c.getGreen() - color.getGreen()) < 12
                    && Math.abs(c.getBlue() - color.getBlue()) < 12) count++;
            }
        }
        return count;
    }
}
