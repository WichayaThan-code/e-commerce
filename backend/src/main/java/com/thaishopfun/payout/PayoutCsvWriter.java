package com.thaishopfun.payout;

import org.springframework.stereotype.Component;

import java.util.List;

/** CSV export for withdrawal/transfer history. Masked account only; formula-injection guarded. */
@Component
public class PayoutCsvWriter {
    public String write(List<PayoutDtos.WithdrawalView> rows) {
        var sb = new StringBuilder();
        sb.append("id,shopId,shopName,status,amount,currency,accountProvider,accountMasked,requestedAt,paidAt,transferReference,transferDate\n");
        for (var row : rows) {
            sb.append(csv(row.id())).append(',')
                .append(csv(row.shopId())).append(',')
                .append(csv(row.shopName())).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.amount())).append(',')
                .append(csv(row.currency())).append(',')
                .append(csv(row.accountProvider())).append(',')
                .append(csv(row.accountNumberMasked())).append(',')
                .append(csv(row.requestedAt())).append(',')
                .append(csv(row.paidAt())).append(',')
                .append(csv(row.transferReference())).append(',')
                .append(csv(row.transferDate()))
                .append('\n');
        }
        return sb.toString();
    }

    private static String csv(Object value) {
        if (value == null) return "";
        var text = String.valueOf(value);
        if (!text.isEmpty() && "=@+-".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
