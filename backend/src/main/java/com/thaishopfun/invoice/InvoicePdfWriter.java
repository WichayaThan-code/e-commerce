package com.thaishopfun.invoice;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Snapshot PDF (not e-Tax). Uses embedded Noto Sans Thai (OFL). */
@Component
public class InvoicePdfWriter {
    private static final ZoneId BKK = ZoneId.of("Asia/Bangkok");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("th"))
        .withZone(BKK);

    public byte[] write(InvoiceDtos.InvoiceDetail doc) {
        try {
            var regular = loadFont("fonts/NotoSansThai-Regular.ttf");
            var bold = loadFont("fonts/NotoSansThai-Bold.ttf");
            var titleFont = new Font(bold, 16);
            var headFont = new Font(bold, 11);
            var bodyFont = new Font(regular, 10);
            var smallFont = new Font(regular, 8);
            var mono = new Font(regular, 10);

            var baos = new ByteArrayOutputStream();
            var document = new Document(PageSize.A4, 36, 36, 42, 42);
            var writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new Footer(smallFont, doc));
            document.open();

            String typeLabel = "TAX_INVOICE".equals(doc.docType()) ? "ใบกำกับภาษี / Tax Invoice" : "ใบแจ้งหนี้ / Invoice";
            if ("DRAFT".equals(doc.status())) typeLabel = "[DRAFT] " + typeLabel;
            if ("CANCELLED".equals(doc.status())) typeLabel = "[CANCELLED] " + typeLabel;

            document.add(new Paragraph(typeLabel, titleFont));
            document.add(new Paragraph(
                "เลขที่: " + (doc.documentNumber() == null ? "—" : doc.documentNumber())
                    + "   สถานะ: " + doc.status(),
                bodyFont
            ));
            if (doc.issuedAt() != null) {
                document.add(new Paragraph("วันที่ออก: " + DT.format(doc.issuedAt()), bodyFont));
            }
            document.add(new Paragraph(" ", bodyFont));

            var parties = new PdfPTable(2);
            parties.setWidthPercentage(100);
            parties.addCell(box("ผู้ขาย / Seller", sellerBlock(doc), headFont, bodyFont));
            parties.addCell(box("ผู้ซื้อ / Buyer", buyerBlock(doc), headFont, bodyFont));
            document.add(parties);
            document.add(new Paragraph(" ", bodyFont));

            if (doc.sourceRef() != null) {
                document.add(new Paragraph("อ้างอิงออเดอร์: " + doc.sourceRef(), bodyFont));
            }

            var table = new PdfPTable(new float[]{0.6f, 3.2f, 1f, 1.2f, 1.2f, 1.3f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : new String[]{"#", "รายการ", "จำนวน", "ราคา", "ส่วนลด", "รวม"}) {
                var cell = new PdfPCell(new Phrase(h, headFont));
                cell.setBackgroundColor(new Color(237, 246, 253));
                cell.setPadding(6);
                table.addCell(cell);
            }
            for (var line : doc.lines()) {
                table.addCell(cell(String.valueOf(line.lineNo()), mono));
                table.addCell(cell(line.description(), bodyFont));
                table.addCell(cell(line.quantity().stripTrailingZeros().toPlainString(), mono));
                table.addCell(cell(baht(line.unitPrice()), mono));
                table.addCell(cell(baht(line.lineDiscount()), mono));
                table.addCell(cell(baht(line.lineSubtotal()), mono));
            }
            document.add(table);
            document.add(new Paragraph(" ", bodyFont));

            document.add(new Paragraph("ยอดสินค้า: " + baht(doc.subtotal()), bodyFont));
            document.add(new Paragraph("ส่วนลดบิล: " + baht(doc.discountTotal()), bodyFont));
            document.add(new Paragraph("ค่าจัดส่ง: " + baht(doc.shippingTotal()), bodyFont));
            if (doc.vatRateApplied() != null) {
                document.add(new Paragraph(
                    "ฐานภาษี: " + baht(doc.taxableBase())
                        + "   VAT " + pct(doc.vatRateApplied()) + ": " + baht(doc.taxAmount()),
                    bodyFont
                ));
            } else {
                document.add(new Paragraph("ภาษี: " + baht(doc.taxAmount()) + " (เอกสารนี้ไม่ใช่ใบกำกับภาษี)", bodyFont));
            }
            document.add(new Paragraph("ยอดรวมสุทธิ: " + baht(doc.grandTotal()) + " " + doc.currency(), headFont));
            document.add(new Paragraph(" ", smallFont));
            document.add(new Paragraph(doc.disclaimer(), smallFont));
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF_RENDER_FAILED", e);
        }
    }

    private static String sellerBlock(InvoiceDtos.InvoiceDetail doc) {
        return doc.sellerLegalName()
            + "\n" + doc.sellerAddress()
            + (doc.sellerTaxId() != null ? "\nเลขผู้เสียภาษี: " + doc.sellerTaxId() : "")
            + (doc.sellerBranchCode() != null ? "\nสาขา: " + doc.sellerBranchCode() : "")
            + (doc.sellerVatRegistered() ? "\nจดทะเบียน VAT: ใช่" : "\nจดทะเบียน VAT: ไม่ใช่");
    }

    private static String buyerBlock(InvoiceDtos.InvoiceDetail doc) {
        return doc.buyerName()
            + "\n" + doc.buyerAddress()
            + (doc.buyerTaxId() != null ? "\nเลขผู้เสียภาษี: " + doc.buyerTaxId() : "")
            + (doc.buyerPhone() != null ? "\nโทร: " + doc.buyerPhone() : "")
            + (doc.buyerEmail() != null ? "\nอีเมล: " + doc.buyerEmail() : "");
    }

    private static PdfPCell box(String title, String body, Font head, Font text) {
        var p = new Paragraph();
        p.add(new Phrase(title + "\n", head));
        p.add(new Phrase(body, text));
        var cell = new PdfPCell(p);
        cell.setPadding(8);
        cell.setBorderColor(new Color(215, 229, 243));
        return cell;
    }

    private static PdfPCell cell(String text, Font font) {
        var c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(5);
        return c;
    }

    private static String baht(BigDecimal v) {
        return v == null ? "0.00" : String.format(Locale.US, "%,.2f", v);
    }

    private static String pct(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private static BaseFont loadFont(String path) throws Exception {
        var resource = new ClassPathResource(path);
        return BaseFont.createFont(resource.getURL().toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }

    private static final class Footer extends com.lowagie.text.pdf.PdfPageEventHelper {
        private final Font font;
        private final InvoiceDtos.InvoiceDetail doc;

        Footer(Font font, InvoiceDtos.InvoiceDetail doc) {
            this.font = font;
            this.doc = doc;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                var phrase = new Phrase(
                    "หน้า " + writer.getPageNumber()
                        + " · " + (doc.documentNumber() == null ? doc.status() : doc.documentNumber())
                        + " · ไม่ใช่ e-Tax",
                    font
                );
                com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    phrase,
                    (document.right() + document.left()) / 2,
                    document.bottom() - 12,
                    0
                );
            } catch (DocumentException ignored) {
                // footer best-effort
            }
        }
    }
}
