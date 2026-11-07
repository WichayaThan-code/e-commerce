package com.thaishopfun.common.excel;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Writes a non-PII sample workbook under docs/samples for manual format review. */
public final class SampleMerchantExportWriter {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "../docs/samples/merchant-export-sample.xlsx");
        Files.createDirectories(out.getParent());
        try (var book = new MerchantExcelWorkbook()) {
            var meta = new LinkedHashMap<String, String>();
            meta.put("Report", "Sample merchant export (synthetic)");
            meta.put("Shop", "Demo Shop");
            meta.put("Timezone", "Asia/Bangkok");
            meta.put("Note", "No real customer PII — for layout review only");
            book.addMetaSheet("Meta", meta);
            var columns = List.of(
                MerchantExcelWorkbook.Column.text("SKU"),
                MerchantExcelWorkbook.Column.text("Product"),
                MerchantExcelWorkbook.Column.integer("Qty"),
                MerchantExcelWorkbook.Column.money("Amount")
            );
            var rows = new ArrayList<List<Object>>();
            rows.add(row("SKU-001", "Sample tea", 2, "45.00"));
            rows.add(row("SKU-002", "Sample shirt", 1, "390.00"));
            rows.add(row("=CMD|", "Formula-like name", 1, "-12.50"));
            book.addTableSheet("Data", "SampleData", columns, rows);
            Files.write(out, book.toBytes());
            System.out.println("Wrote " + out.toAbsolutePath());
        }
    }

    private static List<Object> row(String sku, String name, int qty, String amount) {
        var r = new ArrayList<Object>(4);
        r.add(sku);
        r.add(name);
        r.add(qty);
        r.add(new BigDecimal(amount));
        return r;
    }
}
