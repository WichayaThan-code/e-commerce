package com.thaishopfun.order;

import com.thaishopfun.auth.AuthDtos;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.cart.Cart;
import com.thaishopfun.cart.CartDtos;
import com.thaishopfun.cart.CartInventoryRepository;
import com.thaishopfun.cart.CartItemRepository;
import com.thaishopfun.cart.CartOwner;
import com.thaishopfun.cart.CartRepository;
import com.thaishopfun.cart.CartService;
import com.thaishopfun.cart.CouponRepository;
import com.thaishopfun.cart.PromotionUsage;
import com.thaishopfun.catalog.InventoryLevel;
import com.thaishopfun.catalog.ProductSkuRepository;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cart → orders, all in one transaction:
 * lock promotions of the applied coupons, re-run {@link CartService#validate} (locks inventory rows FOR UPDATE and
 * re-prices), replay check on the Idempotency-Key, then one PENDING_PAYMENT order per shop with price / option /
 * coupon / shipping snapshots, RESERVE stock (ledger + inventory_reservations), promotion_usages, timeline entry,
 * and finally remove the purchased cart lines.
 */
@Service
public class OrderCheckoutService {
    public static final Duration PAYMENT_TTL = Duration.ofHours(24);
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
    private static final Pattern POSTAL = Pattern.compile("\\d{5}");
    private static final Pattern PHONE = Pattern.compile("[0-9+\\- ]{9,20}");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(OrderQueryService.BANGKOK);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CartService carts;
    private final CartRepository cartRows;
    private final CartItemRepository cartItems;
    private final CouponRepository coupons;
    private final OrderPromotionLockRepository promotionLocks;
    private final OrderPromotionUsageRepository usages;
    private final CartInventoryRepository levels;
    private final ProductSkuRepository skus;
    private final ShopRepository shops;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderInventoryService inventory;
    private final OrderQueryService views;
    private final JdbcTemplate jdbc;
    private final OrderMoneyLedger ledger;
    private final CodSettingsService cod;

    public OrderCheckoutService(CartService carts, CartRepository cartRows, CartItemRepository cartItems, CouponRepository coupons,
                                OrderPromotionLockRepository promotionLocks, OrderPromotionUsageRepository usages,
                                CartInventoryRepository levels, ProductSkuRepository skus, ShopRepository shops, OrderRepository orders,
                                OrderStatusHistoryRepository history, OrderInventoryService inventory, OrderQueryService views,
                                JdbcTemplate jdbc, OrderMoneyLedger ledger, CodSettingsService cod) {
        this.ledger = ledger;
        this.cod = cod;
        this.carts = carts;
        this.cartRows = cartRows;
        this.cartItems = cartItems;
        this.coupons = coupons;
        this.promotionLocks = promotionLocks;
        this.usages = usages;
        this.levels = levels;
        this.skus = skus;
        this.shops = shops;
        this.orders = orders;
        this.history = history;
        this.inventory = inventory;
        this.views = views;
        this.jdbc = jdbc;
    }

    /** 409 CHECKOUT_INVALID with the same issues[] as POST /api/cart/validate. */
    public static class CheckoutInvalidException extends AuthException {
        private final List<CartDtos.Issue> issues;

        public CheckoutInvalidException(List<CartDtos.Issue> issues) {
            super(HttpStatus.CONFLICT, "CHECKOUT_INVALID", "ตะกร้ามีรายการที่ต้องแก้ก่อนสั่งซื้อ",
                issues.stream().filter(i -> "ERROR".equals(i.severity()))
                    .map(i -> new AuthDtos.ApiFieldError(i.field() == null ? "cart" : i.field(), i.code(), i.message())).toList());
            this.issues = List.copyOf(issues);
        }

        public List<CartDtos.Issue> getIssues() { return issues; }
    }

    public static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || !KEY.matcher(idempotencyKey.trim()).matches()) {
            throw new ValidationException(List.of(new FieldViolation("Idempotency-Key", "INVALID",
                "ต้องส่ง header Idempotency-Key 8-100 ตัวอักษร (A-Z a-z 0-9 . _ : -)")));
        }
    }

    /** Replay lookup (own transaction): orders already created for this buyer + key. */
    @Transactional(readOnly = true)
    public OrderDtos.CheckoutResponse replay(UUID buyerId, String idempotencyKey) {
        var existing = orders.findCheckoutReplay(buyerId, prefix(buyerId, idempotencyKey.trim()) + "%");
        return existing.isEmpty() ? null : response(true, existing, OrderActor.buyer(buyerId));
    }

    @Transactional
    public OrderDtos.CheckoutResponse checkout(UUID buyerId, String idempotencyKey, OrderDtos.CheckoutRequest request) {
        requireKey(idempotencyKey);
        var key = idempotencyKey.trim();
        var prefix = prefix(buyerId, key);
        var earlier = orders.findCheckoutReplay(buyerId, prefix + "%");
        if (!earlier.isEmpty()) return response(true, earlier, OrderActor.buyer(buyerId));

        var req = request == null ? new OrderDtos.CheckoutRequest(null, null, null, null) : request;
        // checked after the replay lookup: a retried request with an already-used key still gets its orders back
        if (!Boolean.TRUE.equals(req.acceptTerms())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TERMS_NOT_ACCEPTED",
                "\u0e01\u0e23\u0e38\u0e13\u0e32\u0e22\u0e2d\u0e21\u0e23\u0e31\u0e1a\u0e02\u0e49\u0e2d\u0e01\u0e33\u0e2b\u0e19\u0e14\u0e41\u0e25\u0e30\u0e40\u0e07\u0e37\u0e48\u0e2d\u0e19\u0e44\u0e02\u0e01\u0e48\u0e2d\u0e19\u0e2a\u0e31\u0e48\u0e07\u0e0b\u0e37\u0e49\u0e2d");
        }
        var termsAcceptedAt = Instant.now();
        validateRequest(req);
        var method = PaymentMethod.parse(req.paymentMethod());

        // 1. lock promotions behind applied coupons (usage limits), then validate (locks inventory, re-prices)
        var activeCarts = cartRows.findByBuyerUserIdAndStatusOrderByCreatedDateTimeAsc(buyerId, Cart.ACTIVE);
        var promotionIds = new HashSet<UUID>();
        for (var cart : activeCarts) {
            if (cart.getCouponCode() == null || cart.getCouponCode().isBlank()) continue;
            coupons.findByShopIdAndCode(cart.getShopId(), cart.getCouponCode()).ifPresent(c -> promotionIds.add(c.getPromotionId()));
        }
        if (!promotionIds.isEmpty()) promotionLocks.lockAll(promotionIds);
        var owner = new CartOwner(buyerId, null, null);
        var validation = carts.validate(owner);

        // 2. a concurrent request with the same key may have finished while we waited for the locks
        var raced = orders.findCheckoutReplay(buyerId, prefix + "%");
        if (!raced.isEmpty()) return response(true, raced, OrderActor.buyer(buyerId));
        if (!validation.valid()) throw new CheckoutInvalidException(validation.issues());

        var view = validation.cart();
        var codSettings = codSettings(view, method);
        var skuIds = view.groups().stream().flatMap(g -> g.items().stream()).filter(CartDtos.Line::selected)
            .map(CartDtos.Line::skuId).distinct().toList();
        Map<UUID, InventoryLevel> locked = levels.lockForCheckout(skuIds).stream()
            .collect(Collectors.toMap(level -> level.getSku().getId(), Function.identity()));
        var now = Instant.now();
        var created = new ArrayList<Order>();
        var purchasedItemIds = new ArrayList<UUID>();
        var usedNumbers = new HashSet<String>();

        for (var group : view.groups()) {
            var lines = group.items().stream().filter(CartDtos.Line::selected).toList();
            if (lines.isEmpty()) continue;
            var shop = shops.findByShopId(group.shopId())
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "CHECKOUT_INVALID", "ร้านค้าไม่พร้อมขาย"));
            var order = new Order(shop.getUuid(), orderNumber(usedNumbers), buyerId);
            order.setIdempotencyKey(prefix + group.shopId());
            order.setBuyerNote(OrderTransitionService.clean(req.buyerNote()));
            order.setMerchantConfirmStatus("NOT_REQUIRED");
            order.setPaymentMethod(method);
            order.setTermsAcceptedAt(termsAcceptedAt);

            var totals = group.totals();
            var discount = money(totals.discount());
            var gross = lines.stream().map(l -> money(l.lineTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (gross.compareTo(money(totals.subtotal())) != 0) {
                throw new AuthException(HttpStatus.CONFLICT, "CHECKOUT_INVALID", "ยอดรวมเปลี่ยนระหว่างสั่งซื้อ กรุณาลองใหม่");
            }
            var allocated = allocate(lines, discount, gross);
            for (int index = 0; index < lines.size(); index++) {
                var line = lines.get(index);
                var sku = skus.findById(line.skuId()).orElseThrow();
                var item = new OrderItem(shop.getUuid());
                item.setProductId(line.productId());
                item.setSkuId(line.skuId());
                item.setSkuCodeSnapshot(truncate(line.skuCode(), 64));
                item.setProductNameSnapshot(truncate(line.productName() == null ? "-" : line.productName(), 255));
                item.setOptionLabelSnapshot(truncate(line.optionLabel(), 255));
                item.setBarcodeSnapshot(truncate(sku.getBarcode(), 64));
                item.setImageUrlSnapshot(truncate(line.imageUrl(), 700));
                item.setUnitPrice(money(line.unitPrice()));
                item.setCompareAtPriceSnapshot(line.compareAtPrice() == null ? null : money(line.compareAtPrice()));
                item.setCostSnapshot(sku.getCostPrice() == null ? null : money(sku.getCostPrice()));
                item.setQuantity(line.quantity());
                item.setDiscountAmount(allocated.get(index));
                item.setLineTotal(money(line.lineTotal()).subtract(allocated.get(index)));
                if (line.configuration() != null) {
                    // V43: configured line (re-quoted by carts.validate above) -> copy the snapshot onto the order item
                    item.setConfiguration(new java.util.LinkedHashMap<>(line.configuration()));
                    item.setConfigHash(line.configuration().get("configHash") == null ? null : String.valueOf(line.configuration().get("configHash")));
                }
                item.stamp(buyerId);
                order.addItem(item);
                purchasedItemIds.add(line.id());
            }
            order.setSubtotal(gross);
            order.setDiscountTotal(discount);
            order.setShippingFee(money(totals.shippingFee()));
            order.setGrandTotal(money(totals.grandTotal()));
            if (gross.subtract(discount).add(order.getShippingFee()).compareTo(order.getGrandTotal()) != 0) {
                throw new IllegalStateException("cart totals inconsistent for shop " + group.shopId());
            }
            if (method == PaymentMethod.COD) {
                // COD: nothing to pay or verify now; the shop accepts the order, ships, and collects the total (incl. COD fee) on delivery
                var fee = money(codSettings.get(group.shopId()).fee());
                order.setCodFee(fee);
                order.setGrandTotal(order.getGrandTotal().add(fee));
                order.setMerchantConfirmStatus("AWAITING");
            }
            var shipping = group.shipping();
            if (shipping != null) {
                order.setShippingMethodId(shipping.methodId());
                order.setShippingMethodNameSnapshot(truncate(shipping.methodName(), 150));
                if (shipping.options() != null && shipping.methodId() != null) {
                    shipping.options().stream().filter(o -> shipping.methodId().equals(o.id())).findFirst()
                        .ifPresent(o -> order.setShippingCarrierCodeSnapshot(truncate(o.carrierCode(), 50)));
                }
            }
            var coupon = group.coupon();
            boolean couponUsed = coupon != null && coupon.applicable() && coupon.code() != null;
            if (couponUsed) order.setCouponCode(coupon.code());
            order.setShopCustomerId(customerOf(shop.getUuid(), buyerId));
            order.addAddress(address(req.shippingAddress(), buyerId));
            order.stamp(buyerId);
            orders.saveAndFlush(order);

            for (var item : order.getItems()) {
                var level = locked.get(item.getSkuId());
                if (level == null || level.getAvailable() < item.getQuantity()) {
                    throw new AuthException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "สินค้า " + item.getSkuCodeSnapshot() + " คงเหลือไม่พอ");
                }
                inventory.reserve(order, item, level, now.plus(PAYMENT_TTL), buyerId);
            }
            if (couponUsed) {
                var couponRow = coupons.findByShopIdAndCode(shop.getUuid(), coupon.code()).orElseThrow();
                var amount = money(coupon.discountAmount() == null ? BigDecimal.ZERO : coupon.discountAmount())
                    .add(money(coupon.shippingDiscountAmount() == null ? BigDecimal.ZERO : coupon.shippingDiscountAmount()));
                var usage = new PromotionUsage(couponRow.getPromotionId(), couponRow.getId(), order.getId(), buyerId, amount);
                usage.stamp(buyerId);
                usages.save(usage);
            }
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("event", "ORDER_PLACED");
            metadata.put("grandTotal", order.getGrandTotal());
            metadata.put("paymentMethod", method.name());
            if (method == PaymentMethod.COD) metadata.put("codFee", order.getCodFee());
            if (couponUsed) metadata.put("couponCode", coupon.code());
            var entry = new OrderStatusHistory(order.getId(), null, OrderStatus.PENDING_PAYMENT, buyerId, OrderActorType.BUYER, null);
            entry.setMetadataJson(OrderJson.write(metadata));
            entry.stamp(buyerId);
            history.save(entry);
            ledger.snapshot(order);
            created.add(order);
        }
        if (created.isEmpty()) {
            throw new CheckoutInvalidException(List.of(new CartDtos.Issue("CART_EMPTY", "ERROR", null, null, null, null, "ยังไม่ได้เลือกสินค้าในตะกร้า", null)));
        }

        // 3. purchased lines leave the cart; a used coupon is consumed
        cartItems.deleteAllById(purchasedItemIds);
        for (var cart : activeCarts) {
            if (created.stream().anyMatch(o -> o.getShopId().equals(cart.getShopId()) && o.getCouponCode() != null)) {
                cart.setCouponCode(null);
                cart.stamp(buyerId);
                cartRows.save(cart);
            }
        }
        return response(false, created, OrderActor.buyer(buyerId));
    }

    /**
     * COD must be enabled by every shop in the checkout, and each order total incl. the COD fee must fit the shop's limit;
     * otherwise 409 CHECKOUT_INVALID with PAYMENT_METHOD_NOT_AVAILABLE / COD_LIMIT_EXCEEDED per shop. BANK_TRANSFER is
     * always accepted (the receiving account is checked when the slip is sent: PAYMENT_ACCOUNT_NOT_CONFIGURED).
     */
    private Map<Long, CodSettingsService.Settings> codSettings(CartDtos.CartResponse view, PaymentMethod method) {
        var result = new LinkedHashMap<Long, CodSettingsService.Settings>();
        if (method != PaymentMethod.COD) return result;
        var issues = new ArrayList<CartDtos.Issue>();
        for (var group : view.groups()) {
            if (group.items().stream().noneMatch(CartDtos.Line::selected)) continue;
            var shop = shops.findByShopId(group.shopId())
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "CHECKOUT_INVALID", "ร้านค้าไม่พร้อมขาย"));
            var settings = cod.settings(shop.getUuid());
            if (!settings.enabled()) {
                issues.add(new CartDtos.Issue("PAYMENT_METHOD_NOT_AVAILABLE", "ERROR", group.shopId(), null, null, "paymentMethod",
                    "ร้านนี้ไม่รับเก็บเงินปลายทาง (COD)", Map.of("paymentMethod", PaymentMethod.COD.name())));
                continue;
            }
            var total = money(group.totals().grandTotal()).add(money(settings.fee()));
            if (settings.maxOrderAmount() != null && total.compareTo(settings.maxOrderAmount()) > 0) {
                issues.add(new CartDtos.Issue("COD_LIMIT_EXCEEDED", "ERROR", group.shopId(), null, null, "paymentMethod",
                    "ยอดเกินวงเงินเก็บเงินปลายทางของร้าน ฿" + settings.maxOrderAmount(),
                    Map.of("maxOrderAmount", settings.maxOrderAmount(), "orderTotal", total, "codFee", money(settings.fee()))));
                continue;
            }
            result.put(group.shopId(), settings);
        }
        if (!issues.isEmpty()) throw new CheckoutInvalidException(issues);
        return result;
    }

    private OrderDtos.CheckoutResponse response(boolean replayed, List<Order> list, OrderActor actor) {
        var details = list.stream().map(order -> views.detail(order, actor)).toList();
        var total = list.stream().map(Order::getGrandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderDtos.CheckoutResponse(replayed, details, total, "THB");
    }

    /** Coupon discount spread over lines by line value; the last line takes the rounding remainder. */
    static List<BigDecimal> allocate(List<CartDtos.Line> lines, BigDecimal discount, BigDecimal gross) {
        var result = new ArrayList<BigDecimal>();
        if (discount.signum() <= 0 || gross.signum() <= 0) {
            lines.forEach(l -> result.add(BigDecimal.ZERO.setScale(2)));
            return result;
        }
        var remaining = discount;
        for (int i = 0; i < lines.size(); i++) {
            var lineTotal = money(lines.get(i).lineTotal());
            BigDecimal share;
            if (i == lines.size() - 1) {
                share = remaining;
            } else {
                share = discount.multiply(lineTotal).divide(gross, 2, RoundingMode.DOWN);
            }
            share = share.min(lineTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            remaining = remaining.subtract(share);
            result.add(share);
        }
        return result;
    }

    private void validateRequest(OrderDtos.CheckoutRequest req) {
        var violations = new ArrayList<FieldViolation>();
        var a = req.shippingAddress();
        if (a == null) {
            violations.add(new FieldViolation("shippingAddress", "REQUIRED", "กรุณาระบุที่อยู่จัดส่ง"));
        } else {
            required(a.recipientName(), "shippingAddress.recipientName", "ชื่อผู้รับ", 150, violations);
            required(a.phone(), "shippingAddress.phone", "เบอร์โทรผู้รับ", 30, violations);
            required(a.addressLine1(), "shippingAddress.addressLine1", "ที่อยู่", 255, violations);
            optional(a.addressLine2(), "shippingAddress.addressLine2", 255, violations);
            optional(a.subdistrict(), "shippingAddress.subdistrict", 100, violations);
            required(a.district(), "shippingAddress.district", "อำเภอ/เขต", 100, violations);
            required(a.province(), "shippingAddress.province", "จังหวัด", 100, violations);
            required(a.postalCode(), "shippingAddress.postalCode", "รหัสไปรษณีย์", 10, violations);
            var phone = OrderTransitionService.clean(a.phone());
            if (phone != null && phone.length() <= 30 && !PHONE.matcher(phone).matches()) {
                violations.add(new FieldViolation("shippingAddress.phone", "INVALID", "เบอร์โทรไม่ถูกต้อง"));
            }
            var postal = OrderTransitionService.clean(a.postalCode());
            if (postal != null && postal.length() <= 10 && !POSTAL.matcher(postal).matches()) {
                violations.add(new FieldViolation("shippingAddress.postalCode", "INVALID", "รหัสไปรษณีย์ต้องเป็นตัวเลข 5 หลัก"));
            }
        }
        optional(req.buyerNote(), "buyerNote", 1000, violations);
        if (PaymentMethod.parse(req.paymentMethod()) == null) {
            violations.add(new FieldViolation("paymentMethod", "INVALID", "วิธีชำระเงินต้องเป็น BANK_TRANSFER หรือ COD"));
        }
        if (!violations.isEmpty()) throw new ValidationException(violations);
    }

    private static void required(String value, String field, String label, int max, List<FieldViolation> violations) {
        var clean = OrderTransitionService.clean(value);
        if (clean == null) violations.add(new FieldViolation(field, "REQUIRED", "กรุณาระบุ" + label));
        else if (clean.length() > max) violations.add(new FieldViolation(field, "MAX_LENGTH", label + "ยาวเกิน " + max + " ตัวอักษร"));
    }

    private static void optional(String value, String field, int max, List<FieldViolation> violations) {
        var clean = OrderTransitionService.clean(value);
        if (clean != null && clean.length() > max) violations.add(new FieldViolation(field, "MAX_LENGTH", "ยาวเกิน " + max + " ตัวอักษร"));
    }

    private static OrderAddress address(OrderDtos.AddressRequest a, UUID buyerId) {
        var address = new OrderAddress("SHIPPING");
        address.setRecipientName(OrderTransitionService.clean(a.recipientName()));
        address.setPhone(OrderTransitionService.clean(a.phone()));
        address.setAddressLine1(OrderTransitionService.clean(a.addressLine1()));
        address.setAddressLine2(OrderTransitionService.clean(a.addressLine2()));
        address.setSubdistrict(OrderTransitionService.clean(a.subdistrict()));
        address.setDistrict(OrderTransitionService.clean(a.district()));
        address.setProvince(OrderTransitionService.clean(a.province()));
        address.setPostalCode(OrderTransitionService.clean(a.postalCode()));
        address.stamp(buyerId);
        return address;
    }

    /** Existing live CRM record of this buyer in the shop (V22), if any. */
    private UUID customerOf(UUID shopId, UUID buyerId) {
        var ids = jdbc.queryForList(
            "SELECT id FROM shop_customers WHERE shop_id = ? AND user_id = ? AND deleted_at IS NULL AND status <> 'MERGED' ORDER BY created_date_time",
            UUID.class, shopId, buyerId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String orderNumber(java.util.Set<String> used) {
        var day = DAY.format(Instant.now());
        for (int attempt = 0; attempt < 20; attempt++) {
            var sb = new StringBuilder("TSF-").append(day).append('-');
            for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            var candidate = sb.toString();
            if (used.add(candidate) && !orders.existsByOrderNumber(candidate)) return candidate;
        }
        throw new IllegalStateException("could not generate a unique order number");
    }

    /** "sha256(buyer:key):" — the shop number is appended per order; uq_orders_idempotency_key backs it. */
    static String prefix(UUID buyerId, String key) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest((buyerId + ":" + key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest) + ":";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
