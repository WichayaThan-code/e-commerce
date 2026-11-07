package com.thaishopfun.order;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.excel.MerchantExcelWorkbook;
import com.thaishopfun.common.excel.MerchantExcelWorkbook.Column;
import com.thaishopfun.merchant.Shop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Merchant order list → .xlsx (ORDER_EXPORT). Same filters as the list UI; all matching rows up to the cap. */
@Service
public class OrderExportService {
    public static final int EXPORT_MAX_ROWS = 10_000;
    private final OrderQueryService queries;

    public OrderExportService(OrderQueryService queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public byte[] export(OrderActor actor, Long shopNo, OrderQueryService.Filters filters, Locale locale) {
        Shop shop = queries.requireShop(shopNo);
        int lang = locale == null ? 0 : switch (locale.getLanguage().toLowerCase(Locale.ROOT)) {
            case "en" -> 1;
            case "zh" -> 2;
            default -> 0;
        };

        List<OrderDtos.OrderSummary> all = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            var pageFilters = new OrderQueryService.Filters(
                filters.status(), filters.paymentStatus(), filters.fulfillmentStatus(),
                filters.from(), filters.to(), filters.q(),
                page, OrderQueryService.MAX_PAGE_SIZE, filters.sort(), filters.direction(),
                filters.acceptance(), filters.tag(), filters.paymentMethod(), filters.carrierCode(),
                filters.hasTracking(), filters.customer()
            );
            var result = queries.list(actor, null, pageFilters);
            all.addAll(result.items());
            totalPages = result.totalPages();
            if (all.size() > EXPORT_MAX_ROWS) {
                throw new ValidationException(List.of(new FieldViolation("export", "EXPORT_TOO_LARGE",
                    "ส่งออกได้สูงสุด " + EXPORT_MAX_ROWS + " แถว — ลดตัวกรองแล้วลองใหม่")));
            }
            page++;
        } while (page < totalPages);

        try (var book = new MerchantExcelWorkbook()) {
            LinkedHashMap<String, String> meta = new LinkedHashMap<>();
            meta.put(h(lang, "report"), h(lang, "ordersReport"));
            meta.put(h(lang, "shop"), shopName(shop));
            meta.put(h(lang, "timezone"), "Asia/Bangkok");
            meta.put(h(lang, "generatedAt"), Instant.now().toString());
            meta.put(h(lang, "rowCount"), String.valueOf(all.size()));
            meta.put(h(lang, "filters"), filterSummary(filters));
            book.addMetaSheet("Meta", meta);

            List<Column> columns = List.of(
                Column.text(h(lang, "orderNumber")),
                Column.text(h(lang, "status")),
                Column.text(h(lang, "paymentStatus")),
                Column.text(h(lang, "fulfillmentStatus")),
                Column.text(h(lang, "paymentMethod")),
                Column.money(h(lang, "grandTotal")),
                Column.text(h(lang, "currency")),
                Column.integer(h(lang, "itemCount")),
                Column.integer(h(lang, "totalQuantity")),
                Column.text(h(lang, "customerName")),
                Column.text(h(lang, "carrier")),
                Column.text(h(lang, "tracking")),
                Column.text(h(lang, "tags")),
                Column.datetime(h(lang, "placedAt")),
                Column.datetime(h(lang, "paidAt"))
            );
            List<List<Object>> rows = new ArrayList<>(all.size());
            for (var o : all) {
                var row = new ArrayList<Object>(15);
                row.add(o.orderNumber());
                if (o.awaitingAcceptance()) {
                    for (int i = 0; i < 12; i++) row.add(null);
                    row.add(o.placedAt());
                    row.add(null);
                } else {
                    String tags = o.tags() == null ? "" : o.tags().stream().map(OrderDtos.TagView::name).collect(Collectors.joining(", "));
                    row.add(o.status());
                    row.add(o.paymentStatus());
                    row.add(o.fulfillmentStatus());
                    row.add(o.paymentMethod());
                    row.add(o.grandTotal());
                    row.add(o.currency());
                    row.add(o.itemCount());
                    row.add(o.totalQuantity());
                    row.add(o.customerName());
                    row.add(o.carrierCode());
                    row.add(o.trackingNumber());
                    row.add(tags);
                    row.add(o.placedAt());
                    row.add(o.paidAt());
                }
                rows.add(row);
            }
            book.addTableSheet(h(lang, "orders"), "Orders", columns, rows);
            return book.toBytes();
        }
    }

    public static String suggestedFilename(Long shopId) {
        return MerchantExcelWorkbook.filename("orders-" + shopId, LocalDate.now(MerchantExcelWorkbook.BANGKOK));
    }

    private static String shopName(Shop shop) {
        if (shop.getName() != null && !shop.getName().isBlank()) return shop.getName();
        if (shop.getNameEn() != null && !shop.getNameEn().isBlank()) return shop.getNameEn();
        return String.valueOf(shop.getId());
    }

    private static String filterSummary(OrderQueryService.Filters f) {
        var parts = new ArrayList<String>();
        if (f.status() != null && !f.status().isBlank()) parts.add("status=" + f.status());
        if (f.paymentStatus() != null && !f.paymentStatus().isBlank()) parts.add("payment=" + f.paymentStatus());
        if (f.fulfillmentStatus() != null && !f.fulfillmentStatus().isBlank()) parts.add("fulfillment=" + f.fulfillmentStatus());
        if (f.from() != null) parts.add("from=" + f.from());
        if (f.to() != null) parts.add("to=" + f.to());
        if (f.q() != null && !f.q().isBlank()) parts.add("q=" + f.q());
        if (f.acceptance() != null) parts.add("acceptance=" + f.acceptance());
        if (f.paymentMethod() != null) parts.add("paymentMethod=" + f.paymentMethod());
        if (f.carrierCode() != null) parts.add("carrier=" + f.carrierCode());
        if (f.hasTracking() != null) parts.add("hasTracking=" + f.hasTracking());
        if (f.tag() != null) parts.add("tag=" + f.tag());
        if (f.sort() != null) parts.add("sort=" + f.sort() + ":" + (f.direction() == null ? "desc" : f.direction()));
        return parts.isEmpty() ? "(all)" : String.join("; ", parts);
    }

    private static String h(int lang, String key) {
        return switch (key) {
            case "report" -> pick(lang, "รายงาน", "Report", "报表");
            case "ordersReport" -> pick(lang, "รายการคำสั่งซื้อ", "Orders", "订单列表");
            case "shop" -> pick(lang, "ร้าน", "Shop", "店铺");
            case "timezone" -> pick(lang, "เขตเวลา", "Timezone", "时区");
            case "generatedAt" -> pick(lang, "สร้างเมื่อ", "Generated at", "生成时间");
            case "rowCount" -> pick(lang, "จำนวนแถว", "Row count", "行数");
            case "filters" -> pick(lang, "ตัวกรอง", "Filters", "筛选");
            case "orders" -> pick(lang, "คำสั่งซื้อ", "Orders", "订单");
            case "orderNumber" -> pick(lang, "เลขคำสั่งซื้อ", "Order number", "订单号");
            case "status" -> pick(lang, "สถานะ", "Status", "状态");
            case "paymentStatus" -> pick(lang, "สถานะชำระเงิน", "Payment status", "支付状态");
            case "fulfillmentStatus" -> pick(lang, "สถานะจัดส่ง", "Fulfillment", "履约状态");
            case "paymentMethod" -> pick(lang, "วิธีชำระเงิน", "Payment method", "支付方式");
            case "grandTotal" -> pick(lang, "ยอดรวม", "Grand total", "订单总额");
            case "currency" -> pick(lang, "สกุลเงิน", "Currency", "币种");
            case "itemCount" -> pick(lang, "จำนวนรายการ", "Line items", "明细数");
            case "totalQuantity" -> pick(lang, "จำนวนชิ้น", "Quantity", "件数");
            case "customerName" -> pick(lang, "ชื่อลูกค้า", "Customer", "客户");
            case "carrier" -> pick(lang, "ขนส่ง", "Carrier", "承运商");
            case "tracking" -> pick(lang, "เลขพัสดุ", "Tracking", "运单号");
            case "tags" -> pick(lang, "แท็ก", "Tags", "标签");
            case "placedAt" -> pick(lang, "วันสั่งซื้อ", "Placed at", "下单时间");
            case "paidAt" -> pick(lang, "วันชำระเงิน", "Paid at", "支付时间");
            default -> key;
        };
    }

    private static String pick(int lang, String th, String en, String zh) {
        return lang == 0 ? th : lang == 2 ? zh : en;
    }
}
