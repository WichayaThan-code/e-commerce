package com.thaishopfun.report;

import com.thaishopfun.common.excel.MerchantExcelWorkbook;
import com.thaishopfun.common.excel.MerchantExcelWorkbook.Column;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Excel (.xlsx) export for merchant reports — same metrics/definitions as {@link ReportCsvWriter},
 * with one Excel Table per section and a meta sheet (timezone Asia/Bangkok).
 */
final class ReportXlsxWriter {
    private ReportXlsxWriter() {}

    static byte[] write(MerchantReportsService.Overview o, String tab, String language, String shopLabel) {
        int lang = "th".equals(language) ? 0 : "zh".equals(language) ? 2 : 1;
        try (var book = new MerchantExcelWorkbook()) {
            LinkedHashMap<String, String> meta = new LinkedHashMap<>();
            meta.put(label(lang, "title"), shopLabel == null ? "" : shopLabel);
            meta.put(label(lang, "tab"), tab);
            meta.put(label(lang, "period"), o.from() + " → " + o.to());
            meta.put(label(lang, "previousPeriod"), o.previousFrom() + " → " + o.previousTo());
            meta.put(label(lang, "timezone"), o.timezone());
            meta.put(label(lang, "currency"), o.currency());
            meta.put(label(lang, "generatedAt"), Instant.now().toString());
            meta.put(label(lang, "definitions"), label(lang, "defGmv"));
            meta.put(label(lang, "defItems"), label(lang, "defItems"));
            meta.put(label(lang, "defRefund"), label(lang, "defRefund"));
            meta.put(label(lang, "defFee"), label(lang, "defFee"));
            meta.put(label(lang, "defCustomers"), label(lang, "defCustomers"));
            meta.put(label(lang, "defPromo"), label(lang, "defPromo"));
            meta.put(label(lang, "defShipping"), label(lang, "defShipping"));
            book.addMetaSheet("Meta", meta);

            switch (tab) {
                case "promotions" -> {
                    writeKpiLike(book, lang, label(lang, "secPromo"), promoMetrics(o, lang));
                    writePromoTypes(book, o, lang);
                    writeTopCoupons(book, o, lang);
                }
                case "shipping" -> {
                    writeKpiLike(book, lang, label(lang, "secShipping"), shippingMetrics(o, lang));
                    writeCarriers(book, o, lang);
                    writeKpiLike(book, lang, label(lang, "secShipOps"), shipOpsMetrics(o, lang));
                }
                case "sales", "orders", "fees" -> {
                    writeKpis(book, o, lang);
                    writeSeries(book, o, lang);
                    if (!"fees".equals(tab)) writeChannels(book, o, lang);
                }
                case "products" -> {
                    writeCategories(book, o, lang);
                    writeProducts(book, o, lang);
                }
                case "customers" -> writeCustomers(book, o, lang);
                default -> {
                    writeKpis(book, o, lang);
                    writeSeries(book, o, lang);
                    writeCategories(book, o, lang);
                    writeChannels(book, o, lang);
                    writeProducts(book, o, lang);
                    writeCustomers(book, o, lang);
                    writeKpiLike(book, lang, label(lang, "secPromo"), promoMetrics(o, lang));
                    writePromoTypes(book, o, lang);
                    writeTopCoupons(book, o, lang);
                    writeKpiLike(book, lang, label(lang, "secShipping"), shippingMetrics(o, lang));
                    writeCarriers(book, o, lang);
                    writeKpiLike(book, lang, label(lang, "secShipOps"), shipOpsMetrics(o, lang));
                }
            }
            return book.toBytes();
        }
    }

    private static void writeKpis(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        writeKpiLike(book, lang, label(lang, "secKpi"), List.of(
            metric(lang, "gmv", o.kpis().gmv(), o.kpis().previousGmv(), o.kpis().gmvGrowthPct()),
            metric(lang, "itemSales", o.kpis().itemSales(), o.kpis().previousItemSales(), o.kpis().itemSalesGrowthPct()),
            metric(lang, "paidOrders", o.kpis().paidOrderCount(), o.kpis().previousPaidOrderCount(), o.kpis().paidOrderGrowthPct()),
            metric(lang, "allOrders", o.kpis().allOrderCount(), o.kpis().previousAllOrderCount(), o.kpis().allOrderGrowthPct()),
            metric(lang, "refunded", o.kpis().refundedAmount(), o.kpis().previousRefundedAmount(), o.kpis().refundedGrowthPct()),
            metric(lang, "estFee", o.kpis().estimatedFee(), o.kpis().previousEstimatedFee(), o.kpis().estimatedFeeGrowthPct()),
            metric(lang, "estNet", o.kpis().estimatedNet(), o.kpis().previousEstimatedNet(), o.kpis().estimatedNetGrowthPct())
        ));
    }

    private static List<List<Object>> promoMetrics(MerchantReportsService.Overview o, int lang) {
        var p = o.promotionSummary();
        return List.of(
            metric(lang, "redemptions", p.redemptionCount(), p.previousRedemptionCount(), p.redemptionGrowthPct()),
            metric(lang, "discountTotal", p.discountTotal(), p.previousDiscountTotal(), p.discountGrowthPct()),
            metric(lang, "couponOrders", p.paidOrdersWithCoupon(), p.previousPaidOrdersWithCoupon(), p.couponOrderGrowthPct()),
            metric(lang, "attachRate", p.attachRatePct(), p.previousAttachRatePct(), p.attachRateGrowthPct())
        );
    }

    private static List<List<Object>> shippingMetrics(MerchantReportsService.Overview o, int lang) {
        var s = o.shippingSummary();
        return List.of(
            metric(lang, "shippingFee", s.shippingFeeCollected(), s.previousShippingFeeCollected(), s.shippingFeeGrowthPct()),
            metric(lang, "freeShipOrders", s.freeShippingOrderCount(), s.previousFreeShippingOrderCount(), s.freeShippingGrowthPct()),
            metric(lang, "avgShipFee", s.averageShippingFee(), s.previousAverageShippingFee(), s.averageShippingFeeGrowthPct())
        );
    }

    private static List<List<Object>> shipOpsMetrics(MerchantReportsService.Overview o, int lang) {
        var ops = o.shipmentOps();
        return List.of(
            metric(lang, "dispatched", ops.dispatchedCount(), ops.previousDispatchedCount(), ops.dispatchedGrowthPct()),
            metric(lang, "delivered", ops.deliveredCount(), ops.previousDeliveredCount(), ops.deliveredGrowthPct()),
            metric(lang, "failed", ops.failedCount(), ops.previousFailedCount(), ops.failedGrowthPct()),
            metric(lang, "returned", ops.returnedCount(), ops.previousReturnedCount(), ops.returnedGrowthPct())
        );
    }

    private static void writeCustomers(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        var c = o.customerSummary();
        writeKpiLike(book, lang, label(lang, "secCustomers"), List.of(
            metric(lang, "uniqueCustomers", c.uniqueCustomers(), c.previousUniqueCustomers(), c.uniqueGrowthPct()),
            metric(lang, "newCustomers", c.newCustomers(), c.previousNewCustomers(), c.newGrowthPct()),
            metric(lang, "returningCustomers", c.returningCustomers(), c.previousReturningCustomers(), c.returningGrowthPct()),
            metric(lang, "avgOrders", c.averageOrdersPerCustomer(), c.previousAverageOrdersPerCustomer(), c.averageOrdersGrowthPct()),
            metric(lang, "aov", c.averageOrderValue(), c.previousAverageOrderValue(), c.averageOrderValueGrowthPct())
        ));
    }

    private static void writeKpiLike(MerchantExcelWorkbook book, int lang, String sheet, List<List<Object>> rows) {
        book.addTableSheet(sheet, "Kpi", List.of(
            Column.text(label(lang, "metric")),
            Column.decimal(label(lang, "value")),
            Column.decimal(label(lang, "previous")),
            Column.percent(label(lang, "growthPct"))
        ), rows);
    }

    private static List<Object> metric(int lang, String key, Number value, Number previous, BigDecimal growth) {
        var row = new ArrayList<Object>(4);
        row.add(label(lang, key));
        row.add(num(value));
        row.add(num(previous));
        row.add(growth);
        return row;
    }

    private static void writeSeries(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        List<List<Object>> rows = new ArrayList<>();
        for (var p : o.series()) {
            var row = new ArrayList<Object>(6);
            row.add(p.start()); row.add(p.end()); row.add(num(p.gmv())); row.add(p.orders());
            row.add(num(p.estimatedFee())); row.add(num(p.estimatedNet()));
            rows.add(row);
        }
        book.addTableSheet(label(lang, "secSeries"), "Series", List.of(
            Column.text(label(lang, "start")),
            Column.text(label(lang, "end")),
            Column.money(label(lang, "gmv")),
            Column.integer(label(lang, "orders")),
            Column.money(label(lang, "estFee")),
            Column.money(label(lang, "estNet"))
        ), rows);
    }

    private static void writeCategories(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        List<List<Object>> rows = new ArrayList<>();
        for (var c : o.categories()) {
            String name = switch (c.key()) {
                case "UNCATEGORIZED" -> label(lang, "uncategorized");
                case "OTHER" -> label(lang, "other");
                default -> pick(lang, c.nameTh(), c.nameEn(), c.nameZh(), c.key());
            };
            var row = new ArrayList<Object>(4);
            row.add(name); row.add(num(c.itemSales())); row.add(c.quantity()); row.add(num(c.sharePercent()));
            rows.add(row);
        }
        book.addTableSheet(label(lang, "secCategories"), "Categories", List.of(
            Column.text(label(lang, "category")),
            Column.money(label(lang, "itemSales")),
            Column.integer(label(lang, "quantity")),
            Column.percent(label(lang, "share"))
        ), rows);
    }

    private static void writeChannels(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        List<List<Object>> rows = new ArrayList<>();
        for (var c : o.channels()) {
            var row = new ArrayList<Object>(4);
            row.add(label(lang, c.channel())); row.add(c.orders()); row.add(num(c.gmv())); row.add(num(c.sharePercent()));
            rows.add(row);
        }
        book.addTableSheet(label(lang, "secChannels"), "Channels", List.of(
            Column.text(label(lang, "channel")),
            Column.integer(label(lang, "orders")),
            Column.money(label(lang, "gmv")),
            Column.percent(label(lang, "share"))
        ), rows);
    }

    private static void writeProducts(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        List<List<Object>> rows = new ArrayList<>();
        int i = 1;
        for (var p : o.topProducts()) {
            var row = new ArrayList<Object>(4);
            row.add(i++); row.add(p.name()); row.add(p.quantity()); row.add(num(p.itemSales()));
            rows.add(row);
        }
        book.addTableSheet(label(lang, "secProducts"), "Products", List.of(
            Column.integer(label(lang, "rank")),
            Column.text(label(lang, "product")),
            Column.integer(label(lang, "quantity")),
            Column.money(label(lang, "itemSales"))
        ), rows);
    }

    private static void writePromoTypes(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        List<List<Object>> rows = new ArrayList<>();
        for (var typ : o.promoTypes()) {
            var row = new ArrayList<Object>(4);
            row.add(label(lang, typ.promoType())); row.add(typ.redemptions()); row.add(num(typ.discountTotal())); row.add(num(typ.sharePercent()));
            rows.add(row);
        }
        book.addTableSheet(label(lang, "secPromoTypes"), "PromoTypes", List.of(
            Column.text(label(lang, "promoType")),
            Column.integer(label(lang, "redemptions")),
            Column.money(label(lang, "discountTotal")),
            Column.percent(label(lang, "share"))
        ), rows);
    }

    private static void writeTopCoupons(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        List<List<Object>> rows = new ArrayList<>();
        int i = 1;
        for (var c : o.topCoupons()) {
            var row = new ArrayList<Object>(5);
            row.add(i++); row.add(c.code()); row.add(label(lang, c.promoType())); row.add(c.redemptions()); row.add(num(c.discountTotal()));
            rows.add(row);
        }
        book.addTableSheet(label(lang, "secTopCoupons"), "Coupons", List.of(
            Column.integer(label(lang, "rank")),
            Column.text(label(lang, "couponCode")),
            Column.text(label(lang, "promoType")),
            Column.integer(label(lang, "redemptions")),
            Column.money(label(lang, "discountTotal"))
        ), rows);
    }

    private static void writeCarriers(MerchantExcelWorkbook book, MerchantReportsService.Overview o, int lang) {
        List<List<Object>> rows = new ArrayList<>();
        for (var c : o.carriers()) {
            var row = new ArrayList<Object>(4);
            row.add(c.carrier()); row.add(c.orders()); row.add(num(c.shippingFee())); row.add(num(c.sharePercent()));
            rows.add(row);
        }
        book.addTableSheet(label(lang, "secCarriers"), "Carriers", List.of(
            Column.text(label(lang, "carrier")),
            Column.integer(label(lang, "orders")),
            Column.money(label(lang, "shippingFee")),
            Column.percent(label(lang, "share"))
        ), rows);
    }

    private static Object num(Number n) {
        if (n == null) return null;
        return n instanceof BigDecimal b ? b : n;
    }

    private static String pick(int lang, String th, String en, String zh, String fallback) {
        String v = lang == 0 ? th : lang == 2 ? zh : en;
        if (v == null || v.isBlank()) v = th != null && !th.isBlank() ? th : en;
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Reuse CSV writer labels (same keys / translations). */
    private static String label(int lang, String key) {
        return ReportCsvWriter.label(lang, key);
    }
}
