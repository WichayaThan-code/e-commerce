package com.thaishopfun.report;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a report overview as UTF-8 CSV (with BOM so Excel reads Thai). Aggregates only: no phone, email, address,
 * customer name or raw user id. Text cells starting with = + - @ (or tab / CR) get a leading apostrophe (formula injection);
 * numeric cells are written raw so negative numbers stay numbers. Labels follow the caller's language (th / en / zh).
 */
final class ReportCsvWriter {
    static final Set<String> TABS = Set.of("overview", "sales", "products", "orders", "customers", "promotions", "fees", "shipping");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Map<String, String[]> L = new HashMap<>();

    private static void l(String key, String th, String en, String zh) { L.put(key, new String[]{th, en, zh}); }

    static {
        l("title", "รายงานร้านค้า ThaiShopFun", "ThaiShopFun merchant report", "ThaiShopFun 商家报表");
        l("tab", "ประเภทรายงาน", "Report", "报表类型");
        l("period", "ช่วงวันที่", "Period", "统计周期");
        l("previousPeriod", "ช่วงก่อนหน้า (ความยาวเท่ากัน)", "Previous period (same length)", "上一周期（等长）");
        l("timezone", "เขตเวลา", "Timezone", "时区");
        l("currency", "สกุลเงิน", "Currency", "币种");
        l("generatedAt", "สร้างเมื่อ (UTC)", "Generated at (UTC)", "生成时间 (UTC)");
        l("definitions", "คำจำกัดความตัวชี้วัด", "Metric definitions", "指标定义");
        l("defGmv", "ยอดขายรวม (GMV) = ผลรวมยอดสุทธิของคำสั่งซื้อที่ชำระแล้ว (ไม่รวมยกเลิก/คืนเงินแล้ว) ตามวันที่สั่งซื้อ เวลาไทย",
            "GMV = sum of grand_total of paid orders (excluding cancelled / refunded) by order date, Thailand time",
            "GMV = 已支付订单（不含已取消/已退款）按下单日期（泰国时间）的订单总额之和");
        l("defItems", "ยอดขายสินค้า = ผลรวมยอดรายการสินค้าของคำสั่งซื้อที่ชำระแล้ว (แยกจาก GMV)",
            "Item sales = sum of order item line totals of paid orders (separate from GMV)",
            "商品销售额 = 已支付订单商品明细小计之和（与 GMV 分开）");
        l("defRefund", "ยอดคืนเงิน = การคืนเงินสถานะ COMPLETED ตามวันที่คืนเงินสำเร็จ แสดงแยก ไม่หักออกจาก GMV",
            "Refunded = refunds with status COMPLETED by completion date, reported separately and not subtracted from GMV",
            "退款金额 = 状态为 COMPLETED 的退款（按完成日期），单独列示，不从 GMV 中扣除");
        l("defFee", "ค่าธรรมเนียมและรายได้สุทธิเป็นเพียงประมาณการที่ 5% ของ GMV ไม่ใช่กำไร และไม่ใช่ยอดโอนจริง",
            "Fee and net are ESTIMATES at 5% of GMV. They are not profit and not a settled payout",
            "手续费与净收入仅为按 GMV 5% 的估算值，不是利润，也不是实际结算金额");
        l("defCustomers", "ลูกค้านับจากคำสั่งซื้อที่ชำระแล้ว: สมาชิกนับตามบัญชี ผู้ซื้อ guest จัดกลุ่มตามชื่อผู้รับ (ข้อจำกัด: ชื่อซ้ำถือเป็นคนเดียวกัน) ไฟล์นี้ไม่มีข้อมูลส่วนบุคคล",
            "Customers are counted from paid orders: members by account, guests grouped by recipient name (limitation: same name = same guest). This file contains no personal data",
            "客户按已支付订单统计：会员按账号，访客按收件人姓名分组（限制：同名视为同一人）。本文件不含个人信息");
        l("defPromo", "การใช้โปรโมชั่นนับจาก promotion_usages ตามวันใช้ (used_at) · อัตราติดคูปองนับจากคำสั่งซื้อที่ชำระแล้วที่มีรหัสคูปองตามวันสั่งซื้อ · ไม่มี funnel การแปลงจากยอดเข้าชม",
            "Promotion redemptions use promotion_usages by used_at. Coupon attach rate uses paid orders with a coupon code by placed_at. No view-to-checkout conversion funnel.",
            "促销核销按 promotion_usages.used_at；优惠券附着率按已支付且有券码订单的下单日。无浏览转化漏斗。");
        l("defShipping", "ค่าจัดส่งเก็บจาก orders.shipping_fee ของคำสั่งซื้อที่ชำระแล้วตามวันสั่งซื้อ · สถิติพัสดุนับตามวันจัดส่ง/ส่งถึง/ล้มเหลว/คืน ของ shipments",
            "Shipping fees are SUM(orders.shipping_fee) of paid orders by placed_at. Shipment ops count by dispatched/delivered/failed/returned timestamps on shipments.",
            "运费来自已支付订单的 shipping_fee（按下单日）；物流作业按 shipments 的发货/送达/失败/退回时间统计。");
        l("metric", "ตัวชี้วัด", "Metric", "指标");
        l("value", "ค่า", "Value", "数值");
        l("previous", "ช่วงก่อนหน้า", "Previous", "上一周期");
        l("growthPct", "การเติบโต (%)", "Growth (%)", "增长 (%)");
        l("gmv", "ยอดขายรวม (GMV)", "GMV", "GMV");
        l("itemSales", "ยอดขายสินค้า", "Item sales", "商品销售额");
        l("paidOrders", "จำนวนคำสั่งซื้อที่ชำระแล้ว", "Paid orders", "已支付订单数");
        l("allOrders", "จำนวนคำสั่งซื้อทั้งหมด", "All orders", "全部订单数");
        l("refunded", "ยอดคืนเงินสำเร็จ", "Refunded (completed)", "已完成退款");
        l("estFee", "ค่าธรรมเนียม 5% (ประมาณการ)", "Fee 5% (estimated)", "手续费 5%（估算）");
        l("estNet", "รายได้หลังหักค่าธรรมเนียม (ประมาณการ)", "Net after fee (estimated)", "扣费后收入（估算）");
        l("uniqueCustomers", "ลูกค้าทั้งหมด", "Customers", "客户数");
        l("newCustomers", "ลูกค้าใหม่", "New customers", "新客户");
        l("returningCustomers", "ลูกค้ากลับมาซื้อซ้ำ", "Returning customers", "回头客");
        l("avgOrders", "จำนวนการสั่งซื้อเฉลี่ยต่อลูกค้า", "Average orders per customer", "人均订单数");
        l("aov", "มูลค่าเฉลี่ยต่อคำสั่งซื้อ", "Average order value", "平均订单金额");
        l("secKpi", "ตัวชี้วัดหลัก", "Key metrics", "核心指标");
        l("secSeries", "ยอดขายตามช่วงเวลา", "Sales over time", "销售趋势");
        l("secCategories", "รายได้ตามหมวดสินค้า", "Item sales by category", "按商品分类的销售额");
        l("secChannels", "ยอดขายตามช่องทาง", "Sales by channel", "按渠道销售额");
        l("secProducts", "สินค้าขายดี (Top 5)", "Top products (Top 5)", "热销商品 (Top 5)");
        l("secCustomers", "สรุปลูกค้า", "Customer summary", "客户汇总");
        l("secPromo", "สรุปโปรโมชั่น / คูปอง", "Promotion / coupon summary", "促销/优惠券汇总");
        l("secPromoTypes", "ส่วนลดตามประเภทโปรโมชั่น", "Discount by promotion type", "按促销类型的优惠");
        l("secTopCoupons", "คูปองที่ใช้มาก (Top 5)", "Top coupons (Top 5)", "热门优惠券 (Top 5)");
        l("secShipping", "สรุปค่าจัดส่ง", "Shipping fee summary", "运费汇总");
        l("secCarriers", "ค่าจัดส่งตามผู้ให้บริการ", "Shipping fee by carrier", "按承运商运费");
        l("secShipOps", "สถิติพัสดุในช่วง", "Shipment ops in period", "周期内物流作业");
        l("redemptions", "จำนวนครั้งที่ใช้", "Redemptions", "核销次数");
        l("discountTotal", "ยอดส่วนลดที่ให้", "Discount given", "优惠总额");
        l("couponOrders", "คำสั่งซื้อที่ใช้คูปอง", "Paid orders with coupon", "使用优惠券的已支付订单");
        l("attachRate", "อัตราติดคูปอง (%)", "Coupon attach rate (%)", "优惠券附着率 (%)");
        l("promoType", "ประเภทโปรโมชั่น", "Promotion type", "促销类型");
        l("couponCode", "รหัสคูปอง", "Coupon code", "优惠码");
        l("shippingFee", "ค่าจัดส่งที่เก็บได้", "Shipping fee collected", "已收运费");
        l("freeShipOrders", "คำสั่งซื้อส่งฟรี", "Free-shipping paid orders", "免运费已支付订单");
        l("avgShipFee", "ค่าจัดส่งเฉลี่ย", "Average shipping fee", "平均运费");
        l("carrier", "ผู้ให้บริการขนส่ง", "Carrier", "承运商");
        l("dispatched", "พัสดุที่จัดส่งแล้ว", "Dispatched", "已发货");
        l("delivered", "พัสดุที่ส่งถึง", "Delivered", "已送达");
        l("failed", "จัดส่งล้มเหลว", "Delivery failed", "投递失败");
        l("returned", "พัสดุตีกลับ", "Returned", "已退回");
        l("PERCENT", "เปอร์เซ็นต์", "Percent", "百分比");
        l("FIXED_AMOUNT", "จำนวนเงินคงที่", "Fixed amount", "固定金额");
        l("FREE_SHIPPING", "ส่งฟรี", "Free shipping", "包邮");
        l("start", "เริ่ม", "Start", "开始");
        l("end", "สิ้นสุด", "End", "结束");
        l("orders", "คำสั่งซื้อ", "Orders", "订单");
        l("category", "หมวดสินค้า", "Category", "分类");
        l("quantity", "จำนวน", "Quantity", "数量");
        l("share", "สัดส่วน (%)", "Share (%)", "占比 (%)");
        l("channel", "ช่องทาง", "Channel", "渠道");
        l("rank", "อันดับ", "Rank", "排名");
        l("product", "สินค้า", "Product", "商品");
        l("uncategorized", "ไม่ระบุหมวด", "Uncategorized", "未分类");
        l("other", "อื่น ๆ", "Other", "其他");
        l("UNKNOWN", "อื่น ๆ", "Other", "其他");
        l("STOREFRONT", "เว็บไซต์ร้าน", "Storefront", "店铺网站");
        l("LINE_OA", "LINE OA", "LINE OA", "LINE OA");
        l("SHOPEE", "Shopee", "Shopee", "Shopee");
        l("TIKTOK", "TikTok Shop", "TikTok Shop", "TikTok Shop");
        l("LAZADA", "Lazada", "Lazada", "Lazada");
        l("MANUAL", "บันทึกเอง", "Manual", "手动录入");
        l("OMS_IMPORT", "นำเข้าจาก OMS", "OMS import", "OMS 导入");
    }

    private final int lang;
    private final StringBuilder out = new StringBuilder();

    private ReportCsvWriter(String language) {
        this.lang = "th".equals(language) ? 0 : "zh".equals(language) ? 2 : 1;
    }

    static String normalizeTab(String tab) {
        String t = tab == null || tab.isBlank() ? "overview" : tab.trim().toLowerCase();
        if (!TABS.contains(t)) throw new ValidationException(List.of(new FieldViolation("tab", "INVALID", "tab must be one of " + TABS)));
        return t;
    }

    /** UTF-8 bytes including the BOM. */
    static byte[] write(MerchantReportsService.Overview o, String tab, String language) {
        var w = new ReportCsvWriter(language);
        w.build(o, tab);
        var bytes = new ByteArrayOutputStream();
        bytes.writeBytes(BOM);
        bytes.writeBytes(w.out.toString().getBytes(StandardCharsets.UTF_8));
        return bytes.toByteArray();
    }

    private String t(String key) {
        return label(lang, key);
    }

    /** Package helper for {@link ReportXlsxWriter} — lang index 0=th, 1=en, 2=zh. */
    static String label(int lang, String key) {
        String[] v = L.get(key);
        if (v == null) return key;
        int i = lang < 0 || lang > 2 ? 1 : lang;
        return v[i];
    }

    private void build(MerchantReportsService.Overview o, String tab) {
        row(text(t("title")));
        row(text(t("tab")), text(tab));
        row(text(t("period")), text(o.from()), text(o.to()));
        row(text(t("previousPeriod")), text(o.previousFrom()), text(o.previousTo()));
        row(text(t("timezone")), text(o.timezone()));
        row(text(t("currency")), text(o.currency()));
        row(text(t("generatedAt")), text(Instant.now().toString()));
        row();
        row(text(t("definitions")));
        for (String d : List.of("defGmv", "defItems", "defRefund", "defFee", "defCustomers", "defPromo", "defShipping")) row(text(t(d)));
        row();

        switch (tab) {
            case "promotions" -> promotions(o);
            case "shipping" -> shipping(o);
            case "sales" -> { kpis(o); series(o); channels(o); }
            case "products" -> { categories(o); products(o); }
            case "orders" -> { kpis(o); series(o); channels(o); }
            case "customers" -> customers(o);
            case "fees" -> { kpis(o); series(o); }
            default -> { kpis(o); series(o); categories(o); channels(o); products(o); customers(o); promotions(o); shipping(o); }
        }
    }

    private void promotions(MerchantReportsService.Overview o) {
        var p = o.promotionSummary();
        row(text(t("secPromo")));
        row(text(t("metric")), text(t("value")), text(t("previous")), text(t("growthPct")));
        metric("redemptions", p.redemptionCount(), p.previousRedemptionCount(), p.redemptionGrowthPct());
        metric("discountTotal", p.discountTotal(), p.previousDiscountTotal(), p.discountGrowthPct());
        metric("couponOrders", p.paidOrdersWithCoupon(), p.previousPaidOrdersWithCoupon(), p.couponOrderGrowthPct());
        metric("attachRate", p.attachRatePct(), p.previousAttachRatePct(), p.attachRateGrowthPct());
        row();
        row(text(t("secPromoTypes")));
        row(text(t("promoType")), text(t("redemptions")), text(t("discountTotal")), text(t("share")));
        for (var typ : o.promoTypes()) row(text(t(typ.promoType())), num(typ.redemptions()), num(typ.discountTotal()), num(typ.sharePercent()));
        row();
        row(text(t("secTopCoupons")));
        row(text(t("rank")), text(t("couponCode")), text(t("promoType")), text(t("redemptions")), text(t("discountTotal")));
        int i = 1;
        for (var c : o.topCoupons()) row(num(i++), text(c.code()), text(t(c.promoType())), num(c.redemptions()), num(c.discountTotal()));
        row();
    }

    private void shipping(MerchantReportsService.Overview o) {
        var s = o.shippingSummary();
        row(text(t("secShipping")));
        row(text(t("metric")), text(t("value")), text(t("previous")), text(t("growthPct")));
        metric("shippingFee", s.shippingFeeCollected(), s.previousShippingFeeCollected(), s.shippingFeeGrowthPct());
        metric("freeShipOrders", s.freeShippingOrderCount(), s.previousFreeShippingOrderCount(), s.freeShippingGrowthPct());
        metric("avgShipFee", s.averageShippingFee(), s.previousAverageShippingFee(), s.averageShippingFeeGrowthPct());
        row();
        row(text(t("secCarriers")));
        row(text(t("carrier")), text(t("orders")), text(t("shippingFee")), text(t("share")));
        for (var c : o.carriers()) row(text(c.carrier()), num(c.orders()), num(c.shippingFee()), num(c.sharePercent()));
        row();
        var ops = o.shipmentOps();
        row(text(t("secShipOps")));
        row(text(t("metric")), text(t("value")), text(t("previous")), text(t("growthPct")));
        metric("dispatched", ops.dispatchedCount(), ops.previousDispatchedCount(), ops.dispatchedGrowthPct());
        metric("delivered", ops.deliveredCount(), ops.previousDeliveredCount(), ops.deliveredGrowthPct());
        metric("failed", ops.failedCount(), ops.previousFailedCount(), ops.failedGrowthPct());
        metric("returned", ops.returnedCount(), ops.previousReturnedCount(), ops.returnedGrowthPct());
        row();
    }

    private void kpis(MerchantReportsService.Overview o) {
        var k = o.kpis();
        row(text(t("secKpi")));
        row(text(t("metric")), text(t("value")), text(t("previous")), text(t("growthPct")));
        metric("gmv", k.gmv(), k.previousGmv(), k.gmvGrowthPct());
        metric("itemSales", k.itemSales(), k.previousItemSales(), k.itemSalesGrowthPct());
        metric("paidOrders", k.paidOrderCount(), k.previousPaidOrderCount(), k.paidOrderGrowthPct());
        metric("allOrders", k.allOrderCount(), k.previousAllOrderCount(), k.allOrderGrowthPct());
        metric("refunded", k.refundedAmount(), k.previousRefundedAmount(), k.refundedGrowthPct());
        metric("estFee", k.estimatedFee(), k.previousEstimatedFee(), k.estimatedFeeGrowthPct());
        metric("estNet", k.estimatedNet(), k.previousEstimatedNet(), k.estimatedNetGrowthPct());
        row();
    }

    private void series(MerchantReportsService.Overview o) {
        row(text(t("secSeries")));
        row(text(t("start")), text(t("end")), text(t("gmv")), text(t("orders")), text(t("estFee")), text(t("estNet")));
        for (var p : o.series()) row(text(p.start()), text(p.end()), num(p.gmv()), num(p.orders()), num(p.estimatedFee()), num(p.estimatedNet()));
        row();
    }

    private void categories(MerchantReportsService.Overview o) {
        row(text(t("secCategories")));
        row(text(t("category")), text(t("itemSales")), text(t("quantity")), text(t("share")));
        for (var c : o.categories()) {
            String name = switch (c.key()) {
                case "UNCATEGORIZED" -> t("uncategorized");
                case "OTHER" -> t("other");
                default -> pick(c.nameTh(), c.nameEn(), c.nameZh(), c.key());
            };
            row(text(name), num(c.itemSales()), num(c.quantity()), num(c.sharePercent()));
        }
        row();
    }

    private void channels(MerchantReportsService.Overview o) {
        row(text(t("secChannels")));
        row(text(t("channel")), text(t("orders")), text(t("gmv")), text(t("share")));
        for (var c : o.channels()) row(text(t(c.channel())), num(c.orders()), num(c.gmv()), num(c.sharePercent()));
        row();
    }

    private void products(MerchantReportsService.Overview o) {
        row(text(t("secProducts")));
        row(text(t("rank")), text(t("product")), text(t("quantity")), text(t("itemSales")));
        int i = 1;
        for (var p : o.topProducts()) row(num(i++), text(p.name()), num(p.quantity()), num(p.itemSales()));
        row();
    }

    private void customers(MerchantReportsService.Overview o) {
        var c = o.customerSummary();
        row(text(t("secCustomers")));
        row(text(t("metric")), text(t("value")), text(t("previous")), text(t("growthPct")));
        metric("uniqueCustomers", c.uniqueCustomers(), c.previousUniqueCustomers(), c.uniqueGrowthPct());
        metric("newCustomers", c.newCustomers(), c.previousNewCustomers(), c.newGrowthPct());
        metric("returningCustomers", c.returningCustomers(), c.previousReturningCustomers(), c.returningGrowthPct());
        metric("avgOrders", c.averageOrdersPerCustomer(), c.previousAverageOrdersPerCustomer(), c.averageOrdersGrowthPct());
        metric("aov", c.averageOrderValue(), c.previousAverageOrderValue(), c.averageOrderValueGrowthPct());
        row();
    }

    private String pick(String th, String en, String zh, String fallback) {
        String v = lang == 0 ? th : lang == 2 ? zh : en;
        if (v == null || v.isBlank()) v = th != null && !th.isBlank() ? th : en;
        return v == null || v.isBlank() ? fallback : v;
    }

    private void metric(String key, Number value, Number previous, BigDecimal growth) {
        row(text(t(key)), num(value), num(previous), growth == null ? "" : num(growth));
    }

    // ------------------------------------------------------------------ cells

    /** Free text: formula guard + RFC 4180 quoting. */
    static String text(String raw) {
        if (raw == null) return "";
        String v = raw;
        if (!v.isEmpty()) {
            char c = v.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) v = "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    /** Numbers are generated by us (never user text), so they bypass the formula guard. */
    private static String num(Number n) {
        if (n == null) return "";
        return n instanceof BigDecimal b ? b.toPlainString() : String.valueOf(n);
    }

    private void row(String... cells) {
        out.append(String.join(",", cells)).append("\r\n");
    }
}
