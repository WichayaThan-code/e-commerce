package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.excel.MerchantExcelWorkbook;
import com.thaishopfun.common.excel.MerchantExcelWorkbook.Column;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Merchant shipment list → .xlsx (SHIPMENT_READ). Same filters as the list UI; all matching rows up to the cap. */
@Service
public class ShipmentExportService {
    public static final int EXPORT_MAX_ROWS = 10_000;
    private final ShipmentService shipments;
    private final OrderQueryService queries;

    public ShipmentExportService(ShipmentService shipments, OrderQueryService queries) {
        this.shipments = shipments;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public byte[] export(OrderActor actor, Long shopNo, ShipmentService.Filters filters, Locale locale) {
        queries.requireShop(shopNo);
        int lang = locale == null ? 0 : switch (locale.getLanguage().toLowerCase(Locale.ROOT)) {
            case "en" -> 1;
            case "zh" -> 2;
            default -> 0;
        };

        List<ShipmentDtos.ShipmentSummary> all = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            var pageFilters = new ShipmentService.Filters(
                filters.status(), filters.carrierCode(), filters.trackingNumber(),
                filters.from(), filters.to(), page, OrderQueryService.MAX_PAGE_SIZE
            );
            var result = shipments.list(actor, null, pageFilters);
            all.addAll(result.items());
            totalPages = result.totalPages();
            if (all.size() > EXPORT_MAX_ROWS) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "EXPORT_TOO_LARGE",
                    "ส่งออกได้สูงสุด " + EXPORT_MAX_ROWS + " แถว — ลดตัวกรองแล้วลองใหม่");
            }
            page++;
        } while (page < totalPages);

        if (all.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "EXPORT_EMPTY",
                "ไม่มีพัสดุตามตัวกรองนี้ กรุณาปรับตัวกรองแล้วลองใหม่");
        }

        try (var book = new MerchantExcelWorkbook()) {
            List<Column> columns = List.of(
                Column.text(h(lang, "tracking")),
                Column.text(h(lang, "orderNumber")),
                Column.text(h(lang, "orderStatus")),
                Column.text(h(lang, "carrier")),
                Column.datetime(h(lang, "shippedAt")),
                Column.text(h(lang, "status")),
                Column.datetime(h(lang, "deliveredAt")),
                Column.datetime(h(lang, "lastEventAt"))
            );
            List<List<Object>> rows = new ArrayList<>(all.size());
            for (var row : all) {
                var cells = new ArrayList<Object>(8);
                cells.add(nullToEmpty(row.trackingNumber()));
                cells.add(nullToEmpty(row.orderNumber()));
                cells.add(nullToEmpty(row.orderStatus()));
                cells.add(nullToEmpty(row.carrierCode()));
                cells.add(row.shippedAt());
                cells.add(nullToEmpty(row.status()));
                cells.add(row.deliveredAt());
                cells.add(row.lastTrackingEventAt());
                rows.add(cells);
            }
            book.addTableSheet(h(lang, "shipments"), "Shipments", columns, rows);
            return book.toBytes();
        }
    }

    public static String suggestedFilename(Long shopId) {
        return MerchantExcelWorkbook.filename("shipments-" + shopId, LocalDate.now(MerchantExcelWorkbook.BANGKOK));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String h(int lang, String key) {
        return switch (key) {
            case "shipments" -> pick(lang, "การจัดส่ง", "Shipments", "配送");
            case "tracking" -> pick(lang, "เลขพัสดุ", "Tracking", "运单号");
            case "orderNumber" -> pick(lang, "เลขคำสั่งซื้อ", "Order number", "订单号");
            case "orderStatus" -> pick(lang, "สถานะออเดอร์", "Order status", "订单状态");
            case "carrier" -> pick(lang, "บริษัทขนส่ง", "Carrier", "承运商");
            case "shippedAt" -> pick(lang, "วันที่จัดส่ง", "Shipped at", "发货时间");
            case "status" -> pick(lang, "สถานะ", "Status", "状态");
            case "deliveredAt" -> pick(lang, "ส่งถึงเมื่อ", "Delivered at", "妥投时间");
            case "lastEventAt" -> pick(lang, "อัปเดตล่าสุด", "Last event", "最近事件");
            default -> key;
        };
    }

    private static String pick(int lang, String th, String en, String zh) {
        return lang == 0 ? th : lang == 2 ? zh : en;
    }
}
