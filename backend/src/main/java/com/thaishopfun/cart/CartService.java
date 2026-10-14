package com.thaishopfun.cart;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.catalog.InventoryLevel;
import com.thaishopfun.catalog.Product;
import com.thaishopfun.catalog.ProductImage;
import com.thaishopfun.catalog.ProductImageRepository;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductSku;
import com.thaishopfun.catalog.ProductSkuRepository;
import com.thaishopfun.catalog.ProductStatus;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.ShopStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Buyer / guest shopping cart. Marketplace: one {@link Cart} row per shop; the API response groups them.
 * Does not create orders or inventory reservations — {@link #validate} locks stock rows and re-checks prices so
 * a future checkout/order step can use the same locks / {@code inventory_levels.version}.
 */
@Service
public class CartService {
    public static final int MAX_LINES = 50;
    public static final int MAX_QTY_PER_LINE = 99;
    public static final Duration GUEST_TTL = Duration.ofDays(30);
    public static final Duration BUYER_TTL = Duration.ofDays(90);
    /** Used when a shop has no active shipping_methods yet (same ฿40 as the current cart page mock). */
    public static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("40.00");

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductSkuRepository skus;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ShopRepository shops;
    private final CartInventoryRepository inventory;
    private final CouponRepository coupons;
    private final PromotionRepository promotions;
    private final PromotionUsageRepository usages;
    private final ShippingMethodRepository shippingMethods;
    private final CartAccess access;

    public CartService(
        CartRepository carts, CartItemRepository items, ProductSkuRepository skus, ProductRepository products,
        ProductImageRepository images, ShopRepository shops, CartInventoryRepository inventory,
        CouponRepository coupons, PromotionRepository promotions, PromotionUsageRepository usages,
        ShippingMethodRepository shippingMethods, CartAccess access
    ) {
        this.carts = carts;
        this.items = items;
        this.skus = skus;
        this.products = products;
        this.images = images;
        this.shops = shops;
        this.inventory = inventory;
        this.coupons = coupons;
        this.promotions = promotions;
        this.usages = usages;
        this.shippingMethods = shippingMethods;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public CartDtos.CartResponse get(CartOwner owner) {
        return build(owner, null, null);
    }

    @Transactional(readOnly = true)
    public CartDtos.CountResponse summary(CartOwner owner) {
        var view = build(owner, null, null);
        return new CartDtos.CountResponse(view.counts().lineCount(), view.counts().itemQuantity(),
            view.counts().selectedLineCount(), view.counts().selectedQuantity());
    }

    @Transactional
    public CartDtos.CartResponse add(CartOwner owner, CartDtos.AddItemRequest request) {
        if (request == null || request.skuId() == null) {
            throw new ValidationException(List.of(new FieldViolation("skuId", "REQUIRED", "ต้องระบุ SKU")));
        }
        int requested = request.quantity() == null ? 1 : request.quantity();
        if (requested < 1) {
            throw new ValidationException(List.of(new FieldViolation("quantity", "MIN", "จำนวนต้องอย่างน้อย 1")));
        }
        if (requested > MAX_QTY_PER_LINE) {
            throw new ValidationException(List.of(new FieldViolation("quantity", "MAX",
                "จำนวนต่อรายการไม่เกิน " + MAX_QTY_PER_LINE)));
        }
        var sku = loadSku(request.skuId());
        var product = sku.getProduct();
        var shop = sku.getShop();
        requireSellable(shop, product, sku);
        int available = availableOf(sku.getId());
        var cart = activeCart(owner, shop, true);
        var existing = items.findByCartIdAndSkuId(cart.getId(), sku.getId()).orElse(null);
        int already = existing == null ? 0 : existing.getQuantity();
        int room = Math.max(0, Math.min(MAX_QTY_PER_LINE, available) - already);
        boolean cap = Boolean.TRUE.equals(request.capToAvailable());
        int applied;
        if (requested > room) {
            if (!cap || room < 1) {
                throw insufficient(sku.getId(), requested, available, already);
            }
            applied = room;
        } else {
            applied = requested;
        }
        if (existing == null) {
            ensureLineCapacity(owner, 1);
            existing = new CartItem(cart.getId(), sku.getId(), product.getId(), applied, CartMoney.of(sku.getPrice()));
            existing.stamp(owner.buyerUserId());
        } else {
            existing.setQuantity(already + applied);
            existing.stamp(owner.buyerUserId());
        }
        items.save(existing);
        touch(cart, owner);
        var action = new CartDtos.ActionResult(existing.getId(), sku.getId(), requested, applied, applied != requested,
            applied != requested ? "QUANTITY_ADJUSTED" : null,
            applied != requested ? "เพิ่มได้เพียง " + applied + " ชิ้น (คงเหลือ " + available + ")" : null);
        return build(owner, action, null);
    }

    @Transactional
    public CartDtos.CartResponse update(CartOwner owner, UUID itemId, CartDtos.UpdateItemRequest request) {
        if (request == null) throw new ValidationException(List.of(new FieldViolation(null, "REQUIRED", "ข้อมูลไม่ครบ")));
        var item = requireOwnedItem(owner, itemId);
        var cart = carts.findById(item.getCartId()).orElseThrow(this::missingCart);
        if (request.selected() != null) item.setSelected(request.selected());
        if (request.skuId() != null && !request.skuId().equals(item.getSkuId())) {
            item = switchVariant(owner, cart, item, request.skuId());
            if (item == null) {
                touch(cart, owner);
                return build(owner, null, null);
            }
        }
        if (request.quantity() != null) {
            if (request.quantity() < 1) {
                throw new ValidationException(List.of(new FieldViolation("quantity", "MIN", "จำนวนต้องอย่างน้อย 1")));
            }
            if (request.quantity() > MAX_QTY_PER_LINE) {
                throw new ValidationException(List.of(new FieldViolation("quantity", "MAX",
                    "จำนวนต่อรายการไม่เกิน " + MAX_QTY_PER_LINE)));
            }
            int available = availableOf(item.getSkuId());
            if (request.quantity() > available) {
                throw insufficient(item.getSkuId(), request.quantity(), available, 0);
            }
            item.setQuantity(request.quantity());
        }
        item.stamp(owner.buyerUserId());
        items.save(item);
        touch(cart, owner);
        return build(owner, null, null);
    }

    @Transactional
    public CartDtos.CartResponse remove(CartOwner owner, UUID itemId) {
        var item = requireOwnedItem(owner, itemId);
        var cart = carts.findById(item.getCartId()).orElseThrow(this::missingCart);
        items.delete(item);
        dropEmpty(cart);
        touchAlive(owner);
        return build(owner, null, null);
    }

    @Transactional
    public CartDtos.CartResponse removeMany(CartOwner owner, CartDtos.ItemIdsRequest request) {
        if (request == null || request.itemIds() == null || request.itemIds().isEmpty()) {
            throw new ValidationException(List.of(new FieldViolation("itemIds", "REQUIRED", "ต้องระบุรายการ")));
        }
        for (var id : request.itemIds()) {
            items.findById(id).ifPresent(item -> {
                requireOwnedItem(owner, item.getId());
                var cartId = item.getCartId();
                items.delete(item);
                carts.findById(cartId).ifPresent(this::dropEmpty);
            });
        }
        touchAlive(owner);
        return build(owner, null, null);
    }

    @Transactional
    public CartDtos.CartResponse clear(CartOwner owner, Long shopNo) {
        var list = activeCarts(owner);
        for (var cart : list) {
            if (shopNo != null) {
                var shop = shops.findById(cart.getShopId()).orElse(null);
                if (shop == null || !Objects.equals(shop.getId(), shopNo)) continue;
            }
            items.deleteAll(items.findByCartId(cart.getId()));
            cart.setCouponCode(null);
            cart.setShippingMethodId(null);
            cart.setStatus(Cart.ABANDONED);
            cart.stamp(owner.buyerUserId());
            carts.save(cart);
        }
        return build(owner, null, null);
    }

    @Transactional
    public CartDtos.CartResponse select(CartOwner owner, CartDtos.SelectionRequest request) {
        if (request == null || request.selected() == null) {
            throw new ValidationException(List.of(new FieldViolation("selected", "REQUIRED", "ต้องระบุ selected")));
        }
        var owned = ownedItems(owner);
        var target = request.itemIds() == null || request.itemIds().isEmpty()
            ? owned
            : owned.stream().filter(item -> request.itemIds().contains(item.getId())).toList();
        for (var item : target) {
            item.setSelected(request.selected());
            item.stamp(owner.buyerUserId());
        }
        items.saveAll(target);
        touchAlive(owner);
        return build(owner, null, null);
    }

    @Transactional
    public CartDtos.CartResponse applyCoupon(CartOwner owner, Long shopNo, CartDtos.ApplyCouponRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new ValidationException(List.of(new FieldViolation("code", "REQUIRED", "ต้องกรอกโค้ดคูปอง")));
        }
        var shop = requireShop(shopNo);
        var cart = activeCart(owner, shop, false);
        if (cart == null || items.findByCartId(cart.getId()).isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CART_EMPTY", "ตะกร้าร้านนี้ว่าง");
        }
        var code = request.code().trim().toUpperCase();
        var coupon = coupons.findByShopIdAndCode(shop.getUuid(), code)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "ไม่พบโค้ดคูปองนี้"));
        var promo = promotions.findById(coupon.getPromotionId()).orElseThrow(() ->
            new AuthException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "ไม่พบโค้ดคูปองนี้"));
        var reason = couponBlockReason(coupon, promo, owner.buyerUserId(), CartMoney.zero());
        // min-subtotal is checked when pricing; other hard failures reject apply
        if (reason != null && !"COUPON_MIN_SUBTOTAL_NOT_MET".equals(reason)) {
            throw new AuthException(HttpStatus.CONFLICT, reason, couponMessage(reason));
        }
        cart.setCouponCode(code);
        touch(cart, owner);
        return build(owner, null, null);
    }

    @Transactional
    public CartDtos.CartResponse removeCoupon(CartOwner owner, Long shopNo) {
        var shop = requireShop(shopNo);
        var cart = activeCart(owner, shop, false);
        if (cart != null) {
            cart.setCouponCode(null);
            touch(cart, owner);
        }
        return build(owner, null, null);
    }

    @Transactional
    public CartDtos.CartResponse chooseShipping(CartOwner owner, Long shopNo, CartDtos.ChooseShippingRequest request) {
        var shop = requireShop(shopNo);
        var cart = activeCart(owner, shop, false);
        if (cart == null) throw new AuthException(HttpStatus.BAD_REQUEST, "CART_EMPTY", "ตะกร้าร้านนี้ว่าง");
        if (request == null || request.shippingMethodId() == null) {
            cart.setShippingMethodId(null);
        } else {
            var method = shippingMethods.findByIdAndShopId(request.shippingMethodId(), shop.getUuid())
                .filter(ShippingMethod::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHIPPING_METHOD_NOT_FOUND", "ไม่พบวิธีจัดส่งนี้"));
            cart.setShippingMethodId(method.getId());
        }
        touch(cart, owner);
        return build(owner, null, null);
    }

    /**
     * Re-price from live DB prices, re-check stock (with row locks) and coupons. Does not create an order.
     * Future reservation should call {@link CartInventoryRepository#lockForCheckout} the same way.
     */
    @Transactional
    public CartDtos.ValidationResponse validate(CartOwner owner) {
        var cartList = activeCarts(owner);
        var itemList = items.findByCartIdInOrderByCreatedDateTimeAsc(cartList.stream().map(Cart::getId).toList());
        var selected = itemList.stream().filter(CartItem::isSelected).toList();
        var issues = new ArrayList<CartDtos.Issue>();
        if (selected.isEmpty()) {
            issues.add(issue("CART_EMPTY", "ERROR", null, null, null, null, "ยังไม่ได้เลือกสินค้าในตะกร้า", null));
        }
        var skuIds = selected.stream().map(CartItem::getSkuId).distinct().toList();
        Map<UUID, InventoryLevel> locked = skuIds.isEmpty() ? Map.of()
            : inventory.lockForCheckout(skuIds).stream().collect(Collectors.toMap(level -> level.getSku().getId(), Function.identity()));
        for (var item : selected) {
            var sku = skus.findById(item.getSkuId()).orElse(null);
            var product = sku == null ? null : sku.getProduct();
            var shop = sku == null ? null : sku.getShop();
            Long shopNo = shop == null ? null : shop.getId();
            if (sku == null || product == null || shop == null
                || sku.getDeletedAt() != null || !sku.isActive()
                || product.getDeletedAt() != null || product.getStatus() != ProductStatus.PUBLISHED
                || shop.getStatus() != ShopStatus.APPROVED) {
                issues.add(issue("PRODUCT_UNAVAILABLE", "ERROR", shopNo, item.getId(), item.getSkuId(), "items",
                    "สินค้านี้ไม่พร้อมขายแล้ว", Map.of("availableQuantity", 0)));
                continue;
            }
            var level = locked.get(item.getSkuId());
            int available = level == null ? 0 : level.getAvailable();
            if (available <= 0) {
                issues.add(issue("OUT_OF_STOCK", "ERROR", shopNo, item.getId(), item.getSkuId(), "quantity",
                    "สินค้าหมด", Map.of("availableQuantity", 0, "requestedQuantity", item.getQuantity())));
            } else if (item.getQuantity() > available) {
                issues.add(issue("INSUFFICIENT_STOCK", "ERROR", shopNo, item.getId(), item.getSkuId(), "quantity",
                    "คงเหลือเพียง " + available + " ชิ้น", Map.of("availableQuantity", available, "requestedQuantity", item.getQuantity())));
            }
            var live = CartMoney.of(sku.getPrice());
            var snap = CartMoney.of(item.getUnitPriceSnapshot());
            if (snap.compareTo(live) != 0) {
                issues.add(issue("PRICE_CHANGED", "ERROR", shopNo, item.getId(), item.getSkuId(), "unitPrice",
                    "ราคาสินค้าเปลี่ยนจาก ฿" + snap + " เป็น ฿" + live, Map.of("previousPrice", snap, "currentPrice", live)));
            }
        }
        var view = build(owner, null, null);
        for (var group : view.groups()) {
            if (group.coupon() != null && group.coupon().code() != null && !group.coupon().applicable()) {
                issues.add(issue("COUPON_INVALID", "ERROR", group.shopId(), null, null, "couponCode",
                    group.coupon().message() == null ? "คูปองใช้ไม่ได้" : group.coupon().message(),
                    Map.of("code", group.coupon().code(), "reason", group.coupon().reason())));
            }
        }
        boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
        return new CartDtos.ValidationResponse(valid, Instant.now(), issues, view);
    }

    /** Buyer accepts the current prices: every line's snapshot becomes the live price (clears PRICE_CHANGED). */
    @Transactional
    public CartDtos.CartResponse acceptPrices(CartOwner owner) {
        for (var item : ownedItems(owner)) {
            skus.findById(item.getSkuId()).ifPresent(sku -> {
                item.setUnitPriceSnapshot(CartMoney.of(sku.getPrice()));
                item.stamp(owner.buyerUserId());
                items.save(item);
            });
        }
        return build(owner, null, null);
    }

    public CartDtos.CartResponse emptyGuestCart() {
        var zero = CartMoney.zero();
        return new CartDtos.CartResponse(null, CartMoney.CURRENCY, List.of(), new CartDtos.Totals(zero, zero, zero, zero, zero),
            new CartDtos.Counts(0, 0, 0, 0), null, null, null);
    }

    /** Guest validation: same checks, plus LOGIN_REQUIRED (INFO) because checkout needs a signed-in buyer. */
    @Transactional
    public CartDtos.ValidationResponse validateGuest(CartOwner guest) {
        var result = validate(guest);
        var issues = new ArrayList<>(result.issues());
        issues.add(issue("LOGIN_REQUIRED", "INFO", null, null, null, null, "กรุณาเข้าสู่ระบบก่อนชำระเงิน", null));
        return new CartDtos.ValidationResponse(result.valid(), result.checkedAt(), issues, result.cart());
    }

    /** Move guest cart lines into the buyer's carts (same SKU increments, capped by available). Guest carts become ABANDONED. */
    @Transactional
    public CartDtos.CartResponse merge(CartOwner buyer, String guestToken) {
        if (guestToken == null || guestToken.isBlank()) return build(buyer, null, List.of());
        var guest = new CartOwner(null, guestToken, access.hash(guestToken));
        var notes = new ArrayList<CartDtos.MergeNote>();
        for (var guestCart : activeCarts(guest)) {
            var shop = shops.findById(guestCart.getShopId()).orElse(null);
            if (shop == null) continue;
            var target = activeCart(buyer, shop, true);
            for (var guestItem : items.findByCartId(guestCart.getId())) {
                var sku = skus.findById(guestItem.getSkuId()).orElse(null);
                if (sku == null || sku.getDeletedAt() != null || !sku.isActive()
                    || sku.getProduct().getDeletedAt() != null
                    || sku.getProduct().getStatus() != ProductStatus.PUBLISHED
                    || shop.getStatus() != ShopStatus.APPROVED) {
                    notes.add(new CartDtos.MergeNote(guestItem.getSkuId(), "PRODUCT_UNAVAILABLE",
                        "ข้ามสินค้าที่ไม่พร้อมขาย", guestItem.getQuantity(), 0));
                    continue;
                }
                int available = availableOf(sku.getId());
                var existing = items.findByCartIdAndSkuId(target.getId(), sku.getId()).orElse(null);
                int already = existing == null ? 0 : existing.getQuantity();
                int room = Math.max(0, Math.min(MAX_QTY_PER_LINE, available) - already);
                int applied = Math.min(guestItem.getQuantity(), room);
                if (applied < 1) {
                    notes.add(new CartDtos.MergeNote(sku.getId(), "INSUFFICIENT_STOCK",
                        "คงเหลือไม่พอสำหรับรวมตะกร้า", guestItem.getQuantity(), 0));
                    continue;
                }
                if (existing == null) {
                    if (lineCount(buyer) >= MAX_LINES) {
                        notes.add(new CartDtos.MergeNote(sku.getId(), "CART_FULL",
                            "ตะกร้าเต็มแล้ว", guestItem.getQuantity(), 0));
                        continue;
                    }
                    existing = new CartItem(target.getId(), sku.getId(), sku.getProduct().getId(), applied,
                        CartMoney.of(guestItem.getUnitPriceSnapshot() != null ? guestItem.getUnitPriceSnapshot() : sku.getPrice()));
                    existing.setSelected(guestItem.isSelected());
                    existing.stamp(buyer.buyerUserId());
                } else {
                    existing.setQuantity(already + applied);
                    existing.stamp(buyer.buyerUserId());
                }
                items.save(existing);
                if (applied != guestItem.getQuantity()) {
                    notes.add(new CartDtos.MergeNote(sku.getId(), "QUANTITY_ADJUSTED",
                        "รวมได้เพียง " + applied + " ชิ้น", guestItem.getQuantity(), applied));
                }
            }
            if (guestCart.getCouponCode() != null && target.getCouponCode() == null) {
                target.setCouponCode(guestCart.getCouponCode());
            }
            if (guestCart.getShippingMethodId() != null && target.getShippingMethodId() == null) {
                target.setShippingMethodId(guestCart.getShippingMethodId());
            }
            items.deleteAll(items.findByCartId(guestCart.getId()));
            guestCart.setStatus(Cart.ABANDONED);
            guestCart.stamp(buyer.buyerUserId());
            carts.save(guestCart);
            touch(target, buyer);
        }
        return build(buyer, null, notes);
    }

    // ---- build response ----

    private CartDtos.CartResponse build(CartOwner owner, CartDtos.ActionResult action, List<CartDtos.MergeNote> mergeNotes) {
        purgeExpired(owner);
        var cartList = activeCarts(owner);
        var itemList = items.findByCartIdInOrderByCreatedDateTimeAsc(cartList.stream().map(Cart::getId).toList());
        var skuIds = itemList.stream().map(CartItem::getSkuId).distinct().toList();
        var skuMap = skuIds.isEmpty() ? Map.<UUID, ProductSku>of()
            : skus.findAllById(skuIds).stream().collect(Collectors.toMap(ProductSku::getId, Function.identity()));
        var stockMap = skuIds.isEmpty() ? Map.<UUID, InventoryLevel>of()
            : inventory.findBySkuIds(skuIds).stream().collect(Collectors.toMap(level -> level.getSku().getId(), Function.identity()));
        var shopIds = cartList.stream().map(Cart::getShopId).distinct().toList();
        var shopMap = shops.findAllById(shopIds).stream().collect(Collectors.toMap(Shop::getUuid, Function.identity()));
        var shippingByShop = shopIds.isEmpty() ? Map.<UUID, List<ShippingMethod>>of()
            : shippingMethods.findByShopIdInAndActiveTrue(shopIds).stream()
                .collect(Collectors.groupingBy(ShippingMethod::getShopId));

        Instant expiresAt = cartList.stream().map(Cart::getExpiresAt).filter(Objects::nonNull)
            .min(Comparator.naturalOrder()).orElse(null);

        var groups = new ArrayList<CartDtos.ShopGroup>();
        var overallSub = CartMoney.zero();
        var overallDisc = CartMoney.zero();
        var overallShip = CartMoney.zero();
        var overallShipDisc = CartMoney.zero();
        int lineCount = 0, qty = 0, selLines = 0, selQty = 0;

        for (var cart : cartList) {
            var shop = shopMap.get(cart.getShopId());
            if (shop == null) continue;
            var cartItems = itemList.stream().filter(item -> item.getCartId().equals(cart.getId())).toList();
            if (cartItems.isEmpty()) continue;
            var lines = new ArrayList<CartDtos.Line>();
            var selectedSubtotal = CartMoney.zero();
            int selectedWeight = 0;
            int groupSelQty = 0;
            for (var item : cartItems) {
                var line = toLine(item, skuMap.get(item.getSkuId()), stockMap.get(item.getSkuId()));
                lines.add(line);
                lineCount++;
                qty += line.quantity();
                if (line.selected()) {
                    selLines++;
                    selQty += line.quantity();
                    groupSelQty += line.quantity();
                    selectedSubtotal = CartMoney.of(selectedSubtotal.add(line.lineTotal()));
                    selectedWeight += weightOf(skuMap.get(item.getSkuId())) * line.quantity();
                }
            }
            var couponView = resolveCoupon(cart, shop, owner.buyerUserId(), selectedSubtotal);
            var discount = couponView == null || !couponView.applicable() ? CartMoney.zero()
                : CartMoney.of(couponView.discountAmount());
            var afterDiscount = CartMoney.max(CartMoney.zero(), CartMoney.of(selectedSubtotal.subtract(discount)));
            var shipping = resolveShipping(cart, shop, shippingByShop.getOrDefault(shop.getUuid(), List.of()),
                afterDiscount, selectedWeight, groupSelQty > 0, couponView);
            var shipFee = CartMoney.of(shipping.fee());
            // shippingDiscount = what the free-shipping coupon (or threshold) saved vs the raw fee
            var rawFee = rawShippingFee(cart, shop, shippingByShop.getOrDefault(shop.getUuid(), List.of()),
                afterDiscount, selectedWeight, groupSelQty > 0);
            var shipDisc = CartMoney.max(CartMoney.zero(), CartMoney.of(rawFee.subtract(shipFee)));
            if (couponView != null && couponView.applicable() && "FREE_SHIPPING".equals(couponView.type())) {
                couponView = new CartDtos.CouponView(couponView.code(), couponView.type(), couponView.value(),
                    couponView.maxDiscount(), couponView.minSubtotal(), true, null, null, CartMoney.zero(), shipDisc);
            }
            var totals = new CartDtos.Totals(selectedSubtotal, discount, shipFee, shipDisc,
                CartMoney.of(afterDiscount.add(shipFee)));
            overallSub = CartMoney.of(overallSub.add(selectedSubtotal));
            overallDisc = CartMoney.of(overallDisc.add(discount));
            overallShip = CartMoney.of(overallShip.add(shipFee));
            overallShipDisc = CartMoney.of(overallShipDisc.add(shipDisc));
            groups.add(new CartDtos.ShopGroup(shop.getId(), shop.getName(), shop.getSlug(), lines, couponView, shipping, totals, groupSelQty));
        }

        var summary = new CartDtos.Totals(overallSub, overallDisc, overallShip, overallShipDisc,
            CartMoney.of(overallSub.subtract(overallDisc).add(overallShip)));
        return new CartDtos.CartResponse(
            owner.isGuest() ? owner.guestToken() : null,
            CartMoney.CURRENCY,
            groups,
            summary,
            new CartDtos.Counts(lineCount, qty, selLines, selQty),
            expiresAt,
            action,
            mergeNotes == null || mergeNotes.isEmpty() ? null : mergeNotes
        );
    }

    private CartDtos.Line toLine(CartItem item, ProductSku sku, InventoryLevel level) {
        var product = sku == null ? null : sku.getProduct();
        int available = level == null ? 0 : level.getAvailable();
        var live = sku == null ? CartMoney.zero() : CartMoney.of(sku.getPrice());
        var snap = CartMoney.of(item.getUnitPriceSnapshot() == null ? live : item.getUnitPriceSnapshot());
        boolean priceChanged = snap.compareTo(live) != 0;
        String status = "OK";
        if (sku == null || product == null || sku.getDeletedAt() != null || !sku.isActive()
            || product.getDeletedAt() != null || product.getStatus() != ProductStatus.PUBLISHED
            || product.getShop().getStatus() != ShopStatus.APPROVED) {
            status = "PRODUCT_UNAVAILABLE";
            available = 0;
        } else if (available <= 0) {
            status = "OUT_OF_STOCK";
        } else if (item.getQuantity() > available) {
            status = "INSUFFICIENT_STOCK";
        } else if (priceChanged) {
            status = "PRICE_CHANGED";
        }
        var charge = live; // display / totals use current price; snapshot is kept for change detection
        Map<String, String> options = optionMap(sku);
        return new CartDtos.Line(
            item.getId(),
            item.getSkuId(),
            item.getProductId(),
            product == null ? null : product.getNameTh(),
            product == null ? null : product.getSlug(),
            sku == null ? null : sku.getSkuCode(),
            sku == null ? null : sku.getOptionLabel(),
            options,
            imageUrl(product, sku),
            item.getQuantity(),
            item.isSelected(),
            charge,
            snap,
            sku == null || sku.getCompareAtPrice() == null ? null : CartMoney.of(sku.getCompareAtPrice()),
            priceChanged,
            CartMoney.multiply(charge, item.getQuantity()),
            available,
            Math.min(MAX_QTY_PER_LINE, Math.max(available, item.getQuantity())),
            status
        );
    }

    private CartDtos.CouponView resolveCoupon(Cart cart, Shop shop, UUID buyerId, BigDecimal selectedSubtotal) {
        if (cart.getCouponCode() == null || cart.getCouponCode().isBlank()) return null;
        var coupon = coupons.findByShopIdAndCode(shop.getUuid(), cart.getCouponCode()).orElse(null);
        if (coupon == null) {
            return new CartDtos.CouponView(cart.getCouponCode(), null, null, null, null, false,
                "COUPON_NOT_FOUND", couponMessage("COUPON_NOT_FOUND"), CartMoney.zero(), CartMoney.zero());
        }
        var promo = promotions.findById(coupon.getPromotionId()).orElse(null);
        if (promo == null) {
            return new CartDtos.CouponView(cart.getCouponCode(), null, null, null, null, false,
                "COUPON_NOT_FOUND", couponMessage("COUPON_NOT_FOUND"), CartMoney.zero(), CartMoney.zero());
        }
        var reason = couponBlockReason(coupon, promo, buyerId, selectedSubtotal);
        var type = apiPromoType(promo.getPromoType());
        if (reason != null) {
            return new CartDtos.CouponView(coupon.getCode(), type, CartMoney.of(promo.getValue()),
                promo.getMaxDiscountAmount() == null ? null : CartMoney.of(promo.getMaxDiscountAmount()),
                promo.getMinOrderAmount() == null ? null : CartMoney.of(promo.getMinOrderAmount()),
                false, reason, couponMessage(reason), CartMoney.zero(), CartMoney.zero());
        }
        BigDecimal discount = CartMoney.zero();
        BigDecimal shipDisc = CartMoney.zero();
        switch (promo.getPromoType()) {
            case "PERCENT" -> {
                discount = CartMoney.of(selectedSubtotal.multiply(promo.getValue()).movePointLeft(2));
                if (promo.getMaxDiscountAmount() != null) discount = CartMoney.min(discount, promo.getMaxDiscountAmount());
                discount = CartMoney.min(discount, selectedSubtotal);
            }
            case "FIXED_AMOUNT" -> discount = CartMoney.min(CartMoney.of(promo.getValue()), selectedSubtotal);
            case "FREE_SHIPPING" -> shipDisc = null; // filled after shipping is known; marker via type
            default -> { }
        }
        // FREE_SHIPPING: shippingDiscountAmount is computed in resolveShipping via coupon type check;
        // here we leave discount 0 and shipDisc as null sentinel replaced below.
        if ("FREE_SHIPPING".equals(promo.getPromoType())) {
            return new CartDtos.CouponView(coupon.getCode(), type, CartMoney.of(promo.getValue()),
                promo.getMaxDiscountAmount() == null ? null : CartMoney.of(promo.getMaxDiscountAmount()),
                promo.getMinOrderAmount() == null ? null : CartMoney.of(promo.getMinOrderAmount()),
                true, null, null, CartMoney.zero(), CartMoney.zero());
        }
        return new CartDtos.CouponView(coupon.getCode(), type, CartMoney.of(promo.getValue()),
            promo.getMaxDiscountAmount() == null ? null : CartMoney.of(promo.getMaxDiscountAmount()),
            promo.getMinOrderAmount() == null ? null : CartMoney.of(promo.getMinOrderAmount()),
            true, null, null, discount, CartMoney.zero());
    }

    private CartDtos.ShippingView resolveShipping(
        Cart cart, Shop shop, List<ShippingMethod> methods, BigDecimal afterDiscount, int weightGrams,
        boolean hasSelected, CartDtos.CouponView couponView
    ) {
        var active = methods.stream().filter(ShippingMethod::isActive).toList();
        if (!hasSelected) {
            return new CartDtos.ShippingView(null, null, null, true, CartMoney.zero(), null, null, 0, optionsOf(active, CartMoney.zero(), 0));
        }
        if (active.isEmpty()) {
            // Default flat ฿40 when the shop has not configured shipping yet (matches the current UI mock).
            var fee = CartMoney.of(DEFAULT_SHIPPING_FEE);
            if (couponView != null && couponView.applicable() && "FREE_SHIPPING".equals(couponView.type())) fee = CartMoney.zero();
            return new CartDtos.ShippingView(null, "ค่าจัดส่งมาตรฐาน", "FLAT", true, fee, null, null, weightGrams, List.of());
        }
        ShippingMethod chosen = null;
        boolean auto = true;
        if (cart.getShippingMethodId() != null) {
            chosen = active.stream().filter(method -> method.getId().equals(cart.getShippingMethodId())).findFirst().orElse(null);
            auto = chosen == null;
        }
        if (chosen == null) {
            chosen = active.stream()
                .min(Comparator.comparing(method -> feeOf(method, afterDiscount, weightGrams)))
                .orElse(active.get(0));
        }
        var fee = feeOf(chosen, afterDiscount, weightGrams);
        if (couponView != null && couponView.applicable() && "FREE_SHIPPING".equals(couponView.type()) && fee.signum() > 0) {
            fee = CartMoney.zero();
        }
        BigDecimal threshold = chosen.getMinOrderAmount() == null ? null : CartMoney.of(chosen.getMinOrderAmount());
        BigDecimal toFree = null;
        if (threshold != null && afterDiscount.compareTo(threshold) < 0) {
            toFree = CartMoney.of(threshold.subtract(afterDiscount));
        }
        return new CartDtos.ShippingView(chosen.getId(), chosen.getName(), chosen.getFeeType(), auto, fee,
            threshold, toFree, weightGrams, optionsOf(active, afterDiscount, weightGrams));
    }

    /** Shipping fee before any FREE_SHIPPING coupon (still applies the method's own free-shipping threshold). */
    private BigDecimal rawShippingFee(Cart cart, Shop shop, List<ShippingMethod> methods, BigDecimal afterDiscount,
                                      int weightGrams, boolean hasSelected) {
        if (!hasSelected) return CartMoney.zero();
        var active = methods.stream().filter(ShippingMethod::isActive).toList();
        if (active.isEmpty()) return CartMoney.of(DEFAULT_SHIPPING_FEE);
        ShippingMethod chosen = null;
        if (cart.getShippingMethodId() != null) {
            chosen = active.stream().filter(method -> method.getId().equals(cart.getShippingMethodId())).findFirst().orElse(null);
        }
        if (chosen == null) {
            chosen = active.stream().min(Comparator.comparing(method -> feeOf(method, afterDiscount, weightGrams))).orElse(active.get(0));
        }
        return feeOf(chosen, afterDiscount, weightGrams);
    }

    private List<CartDtos.ShippingOption> optionsOf(List<ShippingMethod> methods, BigDecimal afterDiscount, int weightGrams) {
        return methods.stream()
            .map(method -> new CartDtos.ShippingOption(method.getId(), method.getName(), method.getCarrierCode(),
                method.getFeeType(), feeOf(method, afterDiscount, weightGrams),
                method.getEstimatedDaysMin(), method.getEstimatedDaysMax()))
            .toList();
    }

    private BigDecimal feeOf(ShippingMethod method, BigDecimal afterDiscount, int weightGrams) {
        if (method.getMinOrderAmount() != null && afterDiscount.compareTo(CartMoney.of(method.getMinOrderAmount())) >= 0) {
            return CartMoney.zero();
        }
        return switch (method.getFeeType()) {
            case "FREE" -> CartMoney.zero();
            case "WEIGHT" -> {
                var base = CartMoney.of(method.getFeeAmount());
                var extra = method.getExtraFeePerKg() == null ? CartMoney.zero() : CartMoney.of(method.getExtraFeePerKg());
                int kg = Math.max(1, (int) Math.ceil(Math.max(weightGrams, 1) / 1000.0));
                yield CartMoney.of(base.add(extra.multiply(BigDecimal.valueOf(Math.max(0, kg - 1)))));
            }
            default -> CartMoney.of(method.getFeeAmount());
        };
    }

    private String couponBlockReason(Coupon coupon, Promotion promo, UUID buyerId, BigDecimal selectedSubtotal) {
        if (coupon == null || promo == null) return "COUPON_NOT_FOUND";
        if (!coupon.isActive() || !promo.isActive()) return "COUPON_INACTIVE";
        var now = Instant.now();
        if (promo.getStartsAt() != null && now.isBefore(promo.getStartsAt())) return "COUPON_NOT_STARTED";
        if (promo.getEndsAt() != null && !now.isBefore(promo.getEndsAt())) return "COUPON_EXPIRED";
        if (promo.getUsageLimitTotal() != null && usages.countByPromotionId(promo.getId()) >= promo.getUsageLimitTotal()) {
            return "COUPON_USAGE_LIMIT_REACHED";
        }
        if (promo.getUsageLimitPerBuyer() != null && buyerId != null
            && usages.countByPromotionIdAndBuyerUserId(promo.getId(), buyerId) >= promo.getUsageLimitPerBuyer()) {
            return "COUPON_PER_CUSTOMER_LIMIT_REACHED";
        }
        if (promo.getMinOrderAmount() != null && selectedSubtotal.compareTo(CartMoney.of(promo.getMinOrderAmount())) < 0) {
            return "COUPON_MIN_SUBTOTAL_NOT_MET";
        }
        return null;
    }

    private static String couponMessage(String reason) {
        return switch (reason) {
            case "COUPON_NOT_FOUND" -> "ไม่พบโค้ดคูปองนี้";
            case "COUPON_INACTIVE" -> "คูปองนี้ถูกปิดการใช้งาน";
            case "COUPON_NOT_STARTED" -> "คูปองยังไม่เริ่มใช้";
            case "COUPON_EXPIRED" -> "คูปองหมดอายุแล้ว";
            case "COUPON_USAGE_LIMIT_REACHED" -> "คูปองถูกใช้ครบจำนวนแล้ว";
            case "COUPON_PER_CUSTOMER_LIMIT_REACHED" -> "คุณใช้คูปองนี้ครบจำนวนแล้ว";
            case "COUPON_MIN_SUBTOTAL_NOT_MET" -> "ยอดสินค้ายังไม่ถึงขั้นต่ำของคูปอง";
            default -> "คูปองใช้ไม่ได้";
        };
    }

    private static String apiPromoType(String db) {
        if ("FIXED_AMOUNT".equals(db)) return "FIXED";
        return db;
    }

    private static String dbPromoType(String api) {
        if (api == null) return null;
        return switch (api.trim().toUpperCase()) {
            case "FIXED", "FIXED_AMOUNT" -> "FIXED_AMOUNT";
            case "PERCENT" -> "PERCENT";
            case "FREE_SHIPPING" -> "FREE_SHIPPING";
            default -> null;
        };
    }

    // ---- helpers ----

    /** @return the surviving line, or null when the old line was merged into an existing one and deleted */
    private CartItem switchVariant(CartOwner owner, Cart cart, CartItem item, UUID newSkuId) {
        var current = skus.findById(item.getSkuId()).orElseThrow(() ->
            new AuthException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "ไม่พบรายการในตะกร้า"));
        var next = loadSku(newSkuId);
        if (!next.getProduct().getId().equals(current.getProduct().getId())) {
            throw new ValidationException(List.of(new FieldViolation("skuId", "INVALID",
                "สลับได้เฉพาะตัวเลือกของสินค้าชิ้นเดิม")));
        }
        requireSellable(next.getShop(), next.getProduct(), next);
        if (!next.getShop().getUuid().equals(cart.getShopId())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "SKU ไม่ใช่ของร้านนี้");
        }
        var other = items.findByCartIdAndSkuId(cart.getId(), next.getId()).orElse(null);
        if (other != null && !other.getId().equals(item.getId())) {
            int available = availableOf(next.getId());
            int merged = Math.min(MAX_QTY_PER_LINE, other.getQuantity() + item.getQuantity());
            if (merged > available) throw insufficient(next.getId(), merged, available, 0);
            other.setQuantity(merged);
            other.stamp(owner.buyerUserId());
            items.save(other);
            items.delete(item);
            return null;
        }
        int available = availableOf(next.getId());
        if (item.getQuantity() > available) throw insufficient(next.getId(), item.getQuantity(), available, 0);
        item.setSkuId(next.getId());
        item.setProductId(next.getProduct().getId());
        item.setUnitPriceSnapshot(CartMoney.of(next.getPrice()));
        return item;
    }

    private ProductSku loadSku(UUID skuId) {
        return skus.findById(skuId)
            .filter(sku -> sku.getDeletedAt() == null)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SKU_NOT_FOUND", "ไม่พบสินค้า"));
    }

    private void requireSellable(Shop shop, Product product, ProductSku sku) {
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new AuthException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "ร้านนี้ยังไม่เปิดขาย");
        }
        if (product.getDeletedAt() != null || product.getStatus() != ProductStatus.PUBLISHED) {
            throw new AuthException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "สินค้านี้ยังไม่พร้อมขาย");
        }
        if (!sku.isActive()) {
            throw new AuthException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "ตัวเลือกสินค้านี้ปิดการขายแล้ว");
        }
    }

    private int availableOf(UUID skuId) {
        return inventory.findBySkuIds(List.of(skuId)).stream().findFirst().map(InventoryLevel::getAvailable).orElse(0);
    }

    private CartStockException insufficient(UUID skuId, int requested, int available, int alreadyInCart) {
        return new CartStockException(skuId, requested, available, alreadyInCart);
    }

    private Cart activeCart(CartOwner owner, Shop shop, boolean create) {
        var existing = owner.isBuyer()
            ? carts.findByBuyerUserIdAndShopIdAndStatus(owner.buyerUserId(), shop.getUuid(), Cart.ACTIVE)
            : carts.findByGuestTokenHashAndShopIdAndStatus(owner.guestTokenHash(), shop.getUuid(), Cart.ACTIVE);
        if (existing.isPresent()) {
            var cart = existing.get();
            if (cart.getExpiresAt() != null && cart.getExpiresAt().isBefore(Instant.now())) {
                abandon(cart, owner);
            } else {
                return cart;
            }
        }
        if (!create) return null;
        var cart = new Cart(owner.buyerUserId(), owner.guestTokenHash(), shop.getUuid());
        cart.setExpiresAt(Instant.now().plus(owner.isGuest() ? GUEST_TTL : BUYER_TTL));
        cart.stamp(owner.buyerUserId());
        return carts.save(cart);
    }

    private List<Cart> activeCarts(CartOwner owner) {
        purgeExpired(owner);
        return owner.isBuyer()
            ? carts.findByBuyerUserIdAndStatusOrderByCreatedDateTimeAsc(owner.buyerUserId(), Cart.ACTIVE)
            : carts.findByGuestTokenHashAndStatusOrderByCreatedDateTimeAsc(owner.guestTokenHash(), Cart.ACTIVE);
    }

    private void purgeExpired(CartOwner owner) {
        var list = owner.isBuyer()
            ? carts.findByBuyerUserIdAndStatusOrderByCreatedDateTimeAsc(owner.buyerUserId(), Cart.ACTIVE)
            : carts.findByGuestTokenHashAndStatusOrderByCreatedDateTimeAsc(owner.guestTokenHash(), Cart.ACTIVE);
        var now = Instant.now();
        for (var cart : list) {
            if (cart.getExpiresAt() != null && cart.getExpiresAt().isBefore(now)) abandon(cart, owner);
        }
    }

    private void abandon(Cart cart, CartOwner owner) {
        items.deleteAll(items.findByCartId(cart.getId()));
        cart.setStatus(Cart.ABANDONED);
        cart.stamp(owner.buyerUserId());
        carts.save(cart);
    }

    private void dropEmpty(Cart cart) {
        if (items.findByCartId(cart.getId()).isEmpty()) {
            cart.setCouponCode(null);
            cart.setShippingMethodId(null);
            cart.setStatus(Cart.ABANDONED);
            carts.save(cart);
        }
    }

    private void touch(Cart cart, CartOwner owner) {
        cart.setExpiresAt(Instant.now().plus(owner.isGuest() ? GUEST_TTL : BUYER_TTL));
        cart.stamp(owner.buyerUserId());
        carts.save(cart);
    }

    private void touchAlive(CartOwner owner) {
        for (var cart : activeCarts(owner)) touch(cart, owner);
    }

    private void ensureLineCapacity(CartOwner owner, int adding) {
        if (lineCount(owner) + adding > MAX_LINES) {
            throw new AuthException(HttpStatus.CONFLICT, "CART_FULL",
                "ตะกร้ามีได้สูงสุด " + MAX_LINES + " รายการ");
        }
    }

    private long lineCount(CartOwner owner) {
        var ids = activeCarts(owner).stream().map(Cart::getId).toList();
        return ids.isEmpty() ? 0 : items.countByCartIdIn(ids);
    }

    private CartItem requireOwnedItem(CartOwner owner, UUID itemId) {
        var item = items.findById(itemId)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "ไม่พบรายการในตะกร้า"));
        var cart = carts.findById(item.getCartId())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "ไม่พบรายการในตะกร้า"));
        if (!Cart.ACTIVE.equals(cart.getStatus())) {
            throw new AuthException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "ไม่พบรายการในตะกร้า");
        }
        boolean owned = owner.isBuyer()
            ? Objects.equals(cart.getBuyerUserId(), owner.buyerUserId())
            : Objects.equals(cart.getGuestTokenHash(), owner.guestTokenHash());
        if (!owned) throw new AuthException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "ไม่พบรายการในตะกร้า");
        return item;
    }

    private List<CartItem> ownedItems(CartOwner owner) {
        var ids = activeCarts(owner).stream().map(Cart::getId).toList();
        return ids.isEmpty() ? List.of() : items.findByCartIdInOrderByCreatedDateTimeAsc(ids);
    }

    private Shop requireShop(Long shopNo) {
        return shops.findByShopId(shopNo)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }

    private AuthException missingCart() {
        return new AuthException(HttpStatus.NOT_FOUND, "CART_NOT_FOUND", "ไม่พบตะกร้า");
    }

    private String imageUrl(Product product, ProductSku sku) {
        if (product == null) return null;
        UUID imageId = sku != null && sku.getImage() != null ? sku.getImage().getId() : null;
        if (imageId == null) {
            imageId = images.findByProduct_IdOrderBySortOrderAsc(product.getId()).stream()
                .sorted(Comparator.comparing(ProductImage::isPrimaryImage).reversed())
                .map(ProductImage::getId).findFirst().orElse(null);
        }
        if (imageId == null) return null;
        return "/api/shops/" + product.getShop().getSlug() + "/products/" + product.getId() + "/images/" + imageId;
    }


    private static Map<String, String> optionMap(ProductSku sku) {
        if (sku == null || sku.getOptionValues() == null || sku.getOptionValues().isBlank()) return Map.of();
        try {
            var mapper = new tools.jackson.databind.ObjectMapper();
            var values = mapper.readValue(sku.getOptionValues(), new tools.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, String>>() {});
            return values == null ? Map.of() : Map.copyOf(values);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private static int weightOf(ProductSku sku) {
        if (sku == null) return 0;
        if (sku.getWeightGrams() != null) return Math.max(0, sku.getWeightGrams());
        if (sku.getProduct().getWeightGrams() != null) return Math.max(0, sku.getProduct().getWeightGrams());
        return 0;
    }

    private static CartDtos.Issue issue(String code, String severity, Long shopId, UUID itemId, UUID skuId,
                                        String field, String message, Map<String, Object> details) {
        return new CartDtos.Issue(code, severity, shopId, itemId, skuId, field, message, details);
    }

    // exposed for CouponService type mapping
    static String toDbPromoType(String api) { return dbPromoType(api); }
    static String toApiPromoType(String db) { return apiPromoType(db); }
}
