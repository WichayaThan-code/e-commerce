package com.thaishopfun.invoice;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvoiceCsvWriter {
    public String write(List<InvoiceDtos.InvoiceSummary> rows) {
        var sb = new StringBuilder();
        sb.append("id,docType,status,documentNumber,buyerName,grandTotal,taxAmount,currency,sourceRef,createdAt,issuedAt\n");
        for (var row : rows) {
            sb.append(csv(row.id() == null ? "" : row.id().toString())).append(',')
                .append(csv(row.docType())).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.documentNumber())).append(',')
                .append(csv(row.buyerName())).append(',')
                .append(csv(row.grandTotal() == null ? "" : row.grandTotal().toPlainString())).append(',')
                .append(csv(row.taxAmount() == null ? "" : row.taxAmount().toPlainString())).append(',')
                .append(csv(row.currency())).append(',')
                .append(csv(row.sourceRef())).append(',')
                .append(csv(row.createdAt() == null ? "" : row.createdAt().toString())).append(',')
                .append(csv(row.issuedAt() == null ? "" : row.issuedAt().toString()))
                .append('\n');
        }
        return sb.toString();
    }

    /** Escape CSV and neutralize formula injection. */
    static String csv(String raw) {
        var value = raw == null ? "" : raw;
        if (!value.isEmpty() && "=@+-".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
