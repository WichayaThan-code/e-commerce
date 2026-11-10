package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.customer.CustomerInsightKeys;
import com.thaishopfun.customer.CustomerService;
import com.thaishopfun.customer.ShopCustomer;
import com.thaishopfun.customer.ShopCustomerRepository;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read side: lists with filters / paging / sorting, detail views (with PII masking for merchants) and the timeline. */
@Service
public class OrderQueryService {
    public static final int MAX_PAGE_SIZE = 100;
    static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final Map<String, String> SORTS = Map.of(
        "placedAt", "placedAt", "grandTotal", "grandTotal", "orderNumber", "orderNumber", "status", "status", "paidAt", "paidAt");

    /**
     * acceptance (merchant list only): AWAITING = not accepted yet, ACCEPTED = accepted; null = both.
     * tag / paymentMethod / carrierCode / hasTracking: optional AB2 merchant filters (buyer/admin pass null).
     */
    public record Filters(String status, String paymentStatus, String fulfillmentStatus, String from, String to, String q,
                          Integer page, Integer size, String sort, String direction, String acceptance,
                          UUID tag, String paymentMethod, String carrierCode, Boolean hasTracking, String customer) {
        public Filters(String status, String paymentStatus, String fulfillmentStatus, String from, String to, String q,
                       Integer page, Integer size, String sort, String direction) {
            this(status, paymentStatus, fulfillmentStatus, from, to, q, page, size, sort, direction, null, null, null, null, null, null);
        }

        public Filters(String status, String paymentStatus, String fulfillmentStatus, String from, String to, String q,
                       Integer page, Integer size, String sort, String direction, String acceptance) {
            this(status, paymentStatus, fulfillmentStatus, from, to, q, page, size, sort, direction, acceptance, null, null, null, null, null);
        }

        public Filters(String status, String paymentStatus, String fulfillmentStatus, String from, String to, String q,
                       Integer page, Integer size, String sort, String direction, String acceptance,
                       UUID tag, String paymentMethod, String carrierCode, Boolean hasTracking) {
            this(status, paymentStatus, fulfillmentStatus, from, to, q, page, size, sort, direction, acceptance, tag, paymentMethod, carrierCode, hasTracking, null);
        }
    }

    /** Sort keys that would order orders by hidden data; for merchants they apply to accepted orders only. */
    private static final java.util.Set<String> HIDDEN_SORTS = java.util.Set.of("grandTotal", "status", "paidAt");

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ShipmentRepository shipments;
    private final ShopRepository shops;
    private final ShopCustomerRepository shopCustomers;
    private final OrderAddressRepository orderAddresses;
    private final CustomerInsightKeys customerKeys;
    private final AuthorizationService authz;
    private final OrderMoneyLedger ledger;
    private final OrderTagService tagService;

    public OrderQueryService(OrderRepository orders, OrderStatusHistoryRepository history, ShipmentRepository shipments,
                             ShopRepository shops, ShopCustomerRepository shopCustomers, OrderAddressRepository orderAddresses,
                             CustomerInsightKeys customerKeys, AuthorizationService authz, OrderMoneyLedger ledger,
                             OrderTagService tagService) {
        this.ledger = ledger;
        this.orders = orders;
        this.history = history;
        this.shipments = shipments;
        this.shops = shops;
        this.shopCustomers = shopCustomers;
        this.orderAddresses = orderAddresses;
        this.customerKeys = customerKeys;
        this.authz = authz;
        this.tagService = tagService;
    }

    public Shop requireShop(Long shopNo) {
        return shops.findByShopId(shopNo).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"));
    }

    @Transactional(readOnly = true)
    public OrderDtos.PageResponse<OrderDtos.OrderSummary> list(OrderActor actor, UUID shopFilter, Filters filters) {
        var f = filters == null ? new Filters(null, null, null, null, null, null, null, null, null, null) : filters;
        var violations = new ArrayList<FieldViolation>();
        var status = parseEnum(f.status(), "status", OrderStatus::parse, violations);
        var payment = parseEnum(f.paymentStatus(), "paymentStatus", PaymentStatus::parse, violations);
        var fulfillment = parseEnum(f.fulfillmentStatus(), "fulfillmentStatus", FulfillmentStatus::parse, violations);
        var from = parseDate(f.from(), "from", violations);
        var to = parseDate(f.to(), "to", violations);
        String sortKey = f.sort() == null || f.sort().isBlank() ? "placedAt" : f.sort().trim();
        if (!SORTS.containsKey(sortKey)) violations.add(new FieldViolation("sort", "INVALID", "sort ต้องเป็น " + String.join(", ", SORTS.keySet().stream().sorted().toList())));
        String dir = f.direction() == null || f.direction().isBlank() ? "desc" : f.direction().trim().toLowerCase();
        if (!dir.equals("asc") && !dir.equals("desc")) violations.add(new FieldViolation("direction", "INVALID", "direction ต้องเป็น asc หรือ desc"));
        int page = f.page() == null ? 0 : f.page();
        int size = f.size() == null ? 20 : f.size();
        if (page < 0) violations.add(new FieldViolation("page", "MIN", "page ต้องไม่ติดลบ"));
        if (size < 1 || size > MAX_PAGE_SIZE) violations.add(new FieldViolation("size", "RANGE", "size ต้องอยู่ระหว่าง 1 ถึง " + MAX_PAGE_SIZE));
        if (from != null && to != null && from.isAfter(to)) violations.add(new FieldViolation("from", "RANGE", "วันที่เริ่มต้องไม่เกินวันที่สิ้นสุด"));
        String acceptance = f.acceptance() == null || f.acceptance().isBlank() ? null : f.acceptance().trim().toUpperCase();
        if (acceptance != null && !acceptance.equals("AWAITING") && !acceptance.equals("ACCEPTED")) {
            violations.add(new FieldViolation("acceptance", "INVALID", "acceptance ต้องเป็น AWAITING หรือ ACCEPTED"));
        }
        PaymentMethod paymentMethod = null;
        if (f.paymentMethod() != null && !f.paymentMethod().isBlank()) {
            var rawPm = f.paymentMethod().trim();
            if ("BANK_TRANSFER".equalsIgnoreCase(rawPm)) paymentMethod = PaymentMethod.BANK_TRANSFER;
            else if ("COD".equalsIgnoreCase(rawPm)) paymentMethod = PaymentMethod.COD;
            else violations.add(new FieldViolation("paymentMethod", "INVALID", "paymentMethod ต้องเป็น BANK_TRANSFER หรือ COD"));
        }
        String carrierCode = f.carrierCode() == null || f.carrierCode().isBlank() ? null : f.carrierCode().trim();
        Boolean hasTracking = f.hasTracking();
        UUID tagId = f.tag();
        boolean merchant = actor.type() == OrderActorType.MERCHANT;
        CustomerInsightKeys.Resolved customerKey = null;
        if (merchant && f.customer() != null && !f.customer().isBlank()) {
            customerKey = customerKeys.resolve(actor.shopUuid(), f.customer()).orElseThrow(() ->
                new ValidationException("CUSTOMER_KEY_INVALID",
                    List.of(new FieldViolation("customer", "CUSTOMER_KEY_INVALID", "คีย์ลูกค้าไม่ถูกต้อง"))));
        }
        boolean hiddenSort = merchant && HIDDEN_SORTS.contains(sortKey);
        boolean extraFilter = paymentMethod != null || carrierCode != null || hasTracking != null || tagId != null || customerKey != null;
        String rawQ = f.q() == null || f.q().isBlank() ? null : f.q().trim();
        if (rawQ != null && rawQ.length() > 100) {
            violations.add(new FieldViolation("q", "TOO_LONG", "q ยาวเกิน 100 ตัวอักษร"));
        }
        if (!violations.isEmpty()) throw new ValidationException(violations);

        String normalizedQ = null;
        if (rawQ != null && rawQ.length() <= 100) {
            var cleaned = rawQ.replaceFirst("^#+", "").trim().toLowerCase();
            if (!cleaned.isEmpty()) normalizedQ = cleaned;
        }
        final String q = normalizedQ;
        PaymentMethod paymentMethodFinal = paymentMethod;
        String carrierFinal = carrierCode;
        Boolean hasTrackingFinal = hasTracking;
        UUID tagFinal = tagId;
        CustomerInsightKeys.Resolved customerFinal = customerKey;
        Specification<Order> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var acceptedAt = root.<Instant>get("merchantConfirmedAt");
            if ("AWAITING".equals(acceptance)) predicates.add(cb.isNull(acceptedAt));
            if ("ACCEPTED".equals(acceptance)) predicates.add(cb.isNotNull(acceptedAt));
            // merchants: filters on hidden data only ever match accepted orders (no probing un-accepted ones)
            if (merchant && (status != null || payment != null || fulfillment != null || extraFilter)) {
                predicates.add(cb.isNotNull(acceptedAt));
            }
            switch (actor.type()) {
                case BUYER -> predicates.add(cb.equal(root.get("buyerUserId"), actor.userId()));
                case MERCHANT -> predicates.add(cb.equal(root.get("shopId"), actor.shopUuid()));
                default -> { if (shopFilter != null) predicates.add(cb.equal(root.get("shopId"), shopFilter)); }
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (payment != null) predicates.add(cb.equal(root.get("paymentStatus"), payment));
            if (fulfillment != null) predicates.add(cb.equal(root.get("fulfillmentStatus"), fulfillment));
            if (paymentMethodFinal != null) predicates.add(cb.equal(root.get("paymentMethod"), paymentMethodFinal));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("placedAt"), from.atStartOfDay(BANGKOK).toInstant()));
            if (to != null) predicates.add(cb.lessThan(root.get("placedAt"), to.plusDays(1).atStartOfDay(BANGKOK).toInstant()));
            if (customerFinal != null) {
                if (customerFinal.shopCustomerId() != null) {
                    predicates.add(cb.equal(root.get("shopCustomerId"), customerFinal.shopCustomerId()));
                } else {
                    predicates.add(cb.isNull(root.get("shopCustomerId")));
                    predicates.add(cb.equal(root.get("buyerUserId"), customerFinal.buyerUserId()));
                }
            }
            if (tagFinal != null) {
                var tagged = query.subquery(UUID.class);
                var a = tagged.from(OrderTagAssignment.class);
                tagged.select(a.get("orderId")).where(cb.equal(a.get("orderId"), root.get("id")), cb.equal(a.get("tagId"), tagFinal));
                predicates.add(cb.exists(tagged));
            }
            if (carrierFinal != null || hasTrackingFinal != null) {
                var ship = query.subquery(UUID.class);
                var s = ship.from(Shipment.class);
                var shipPreds = new ArrayList<Predicate>();
                shipPreds.add(cb.equal(s.get("orderId"), root.get("id")));
                shipPreds.add(cb.notEqual(s.get("status"), "CANCELLED"));
                if (carrierFinal != null) shipPreds.add(cb.equal(s.get("carrierCode"), carrierFinal));
                if (Boolean.TRUE.equals(hasTrackingFinal)) {
                    shipPreds.add(cb.isNotNull(s.get("trackingNumber")));
                    shipPreds.add(cb.notEqual(cb.trim(s.get("trackingNumber")), ""));
                }
                ship.select(s.get("id")).where(shipPreds.toArray(Predicate[]::new));
                if (Boolean.FALSE.equals(hasTrackingFinal) && carrierFinal == null) {
                    // no live shipment with a non-blank tracking number
                    var withTracking = query.subquery(UUID.class);
                    var t = withTracking.from(Shipment.class);
                    withTracking.select(t.get("id")).where(
                        cb.equal(t.get("orderId"), root.get("id")),
                        cb.notEqual(t.get("status"), "CANCELLED"),
                        cb.isNotNull(t.get("trackingNumber")),
                        cb.notEqual(cb.trim(t.get("trackingNumber")), ""));
                    predicates.add(cb.not(cb.exists(withTracking)));
                } else if (Boolean.FALSE.equals(hasTrackingFinal)) {
                    predicates.add(cb.exists(ship));
                    var withTracking = query.subquery(UUID.class);
                    var t = withTracking.from(Shipment.class);
                    withTracking.select(t.get("id")).where(
                        cb.equal(t.get("orderId"), root.get("id")),
                        cb.notEqual(t.get("status"), "CANCELLED"),
                        cb.equal(t.get("carrierCode"), carrierFinal),
                        cb.isNotNull(t.get("trackingNumber")),
                        cb.notEqual(cb.trim(t.get("trackingNumber")), ""));
                    predicates.add(cb.not(cb.exists(withTracking)));
                } else {
                    predicates.add(cb.exists(ship));
                }
            }
            if (q != null) {
                var like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var byOrderNumber = cb.like(cb.lower(root.get("orderNumber")), like, '\\');
                var address = query.subquery(UUID.class);
                var a = address.from(OrderAddress.class);
                address.select(a.get("id")).where(cb.equal(a.get("order"), root), cb.or(
                    cb.like(cb.lower(a.get("recipientName")), like, '\\'),
                    cb.like(cb.lower(a.get("phone")), like, '\\')));
                // merchants: buyer name / phone search only over accepted orders
                var byAddress = merchant ? cb.and(cb.isNotNull(acceptedAt), cb.exists(address)) : cb.exists(address);
                // buyer / admin: also match shop name, nameEn, slug (case-insensitive partial)
                if (actor.type() == OrderActorType.BUYER || actor.type() == OrderActorType.ADMIN) {
                    var shopSub = query.subquery(UUID.class);
                    var s = shopSub.from(Shop.class);
                    shopSub.select(s.get("id")).where(cb.equal(s.get("id"), root.get("shopId")), cb.or(
                        cb.like(cb.lower(s.get("name")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(s.get("nameEn"), cb.literal(""))), like, '\\'),
                        cb.like(cb.lower(s.get("slug")), like, '\\')));
                    predicates.add(cb.or(byOrderNumber, byAddress, cb.exists(shopSub)));
                } else {
                    predicates.add(cb.or(byOrderNumber, byAddress));
                }
            }
            if (hiddenSort && query.getResultType() != Long.class && query.getResultType() != long.class) {
                // ORDER BY (CASE WHEN accepted THEN key END), placed_at DESC, id: un-accepted orders never ordered by hidden data
                var key = root.get(SORTS.get(sortKey));
                var masked = cb.selectCase().when(cb.isNull(acceptedAt), cb.nullLiteral(key.getJavaType())).otherwise(key);
                query.orderBy(dir.equals("asc") ? cb.asc(masked) : cb.desc(masked), cb.desc(root.get("placedAt")), cb.asc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var sort = hiddenSort ? Sort.unsorted()
            : Sort.by(dir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, SORTS.get(sortKey)).and(Sort.by(Sort.Direction.ASC, "id"));
        var result = orders.findAll(spec, PageRequest.of(page, size, sort));
        var shopMap = shopMap(result.getContent().stream().map(Order::getShopId).toList());
        var orderIds = result.getContent().stream().map(Order::getId).toList();
        var tagMap = actor.type() == OrderActorType.MERCHANT ? tagService.tagsByOrderIds(orderIds) : Map.<UUID, List<OrderDtos.TagView>>of();
        var shipMap = latestShipments(orderIds);
        Map<UUID, String> customerDisplayById = Map.of();
        Map<UUID, String> recipientByOrderId = Map.of();
        boolean canReadPii = false;
        if (merchant) {
            Shop shopForPii = result.getContent().isEmpty() ? null : shopMap.get(result.getContent().get(0).getShopId());
            canReadPii = shopForPii != null && actor.userId() != null
                && authz.checkShop(actor.userId(), shopForPii.getId(), "CUSTOMER_PII_READ") == AuthorizationService.ShopDecision.GRANTED;
            var accepted = result.getContent().stream().filter(o -> o.getMerchantConfirmedAt() != null).toList();
            if (!accepted.isEmpty() && shopForPii != null) {
                var customerIds = accepted.stream().map(Order::getShopCustomerId).filter(Objects::nonNull).distinct().toList();
                if (!customerIds.isEmpty()) {
                    customerDisplayById = shopCustomers.findByIdInAndShopIdAndDeletedAtIsNull(customerIds, shopForPii.getUuid())
                        .stream().collect(Collectors.toMap(ShopCustomer::getId, ShopCustomer::getDisplayName, (a, b) -> a));
                }
                var acceptedIds = accepted.stream().map(Order::getId).toList();
                recipientByOrderId = recipientNames(acceptedIds);
            }
        }
        boolean pii = canReadPii;
        Map<UUID, String> displays = customerDisplayById;
        Map<UUID, String> recipients = recipientByOrderId;
        var items = result.getContent().stream().map(order -> {
            String customerName = null;
            String insightKey = null;
            if (merchant && order.getMerchantConfirmedAt() != null) {
                String raw = null;
                if (order.getShopCustomerId() != null) {
                    raw = displays.get(order.getShopCustomerId());
                }
                if (raw == null || raw.isBlank()) {
                    raw = recipients.get(order.getId());
                }
                if (raw != null && !raw.isBlank()) {
                    customerName = pii ? raw : CustomerService.maskName(raw);
                }
                if (order.getShopCustomerId() != null) {
                    insightKey = customerKeys.encode(order.getShopId(), order.getShopCustomerId(), null);
                } else if (order.getBuyerUserId() != null) {
                    insightKey = customerKeys.encode(order.getShopId(), null, order.getBuyerUserId());
                }
            }
            return summary(order, shopMap.get(order.getShopId()), actor, tagMap.get(order.getId()), shipMap.get(order.getId()),
                customerName, insightKey);
        }).toList();
        return new OrderDtos.PageResponse<>(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail get(OrderActor actor, UUID orderId) {
        return detail(load(actor, orderId), actor);
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.TimelineEntry> timeline(OrderActor actor, UUID orderId) {
        var order = load(actor, orderId);
        if (MerchantVisibility.restricted(order, actor)) {
            // only "order placed" and when; no statuses, actors, reasons or metadata
            return history.findByOrderIdOrderByIdAsc(order.getId()).stream().limit(1)
                .map(entry -> new OrderDtos.TimelineEntry(entry.getId(), "ORDER_PLACED", null, null, null, null, null, null, order.getPlacedAt()))
                .toList();
        }
        return history.findByOrderIdOrderByIdAsc(order.getId()).stream().map(entry -> {
            var metadata = OrderJson.read(entry.getMetadataJson());
            String named = metadata != null && metadata.get("event") instanceof String value && !value.isBlank() ? value : null;
            // from = to rows (accept in place, note / tag / money events) are not status changes: keep them only as their
            // named event with fromStatus = null (toStatus = status at that moment); drop unnamed ones (would be a fake STATUS_CHANGED)
            boolean sameStatus = entry.getFromStatus() != null && entry.getFromStatus().equals(entry.getToStatus());
            if (sameStatus && named == null) return null;
            String event = named != null ? named : entry.getFromStatus() == null ? "ORDER_PLACED" : "STATUS_CHANGED";
            // buyers do not see which staff account acted
            UUID actorUser = actor.type() == OrderActorType.BUYER && !"BUYER".equals(entry.getChangedByType()) ? null : entry.getChangedBy();
            return new OrderDtos.TimelineEntry(entry.getId(), event, sameStatus ? null : entry.getFromStatus(), entry.getToStatus(),
                entry.getChangedByType(), actorUser, entry.getReason(), metadata, entry.getCreatedDateTime());
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.AllowedTransitions allowed(OrderActor actor, UUID orderId) {
        var order = load(actor, orderId);
        if (MerchantVisibility.restricted(order, actor)) {
            var actions = new ArrayList<OrderDtos.AllowedTransition>();
            if (OrderStateMachine.canAccept(order.getStatus(), actor.type())) actions.add(new OrderDtos.AllowedTransition(null, "ACCEPT", false, false));
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) actions.add(new OrderDtos.AllowedTransition(OrderStatus.CANCELLED.name(), "CANCEL", true, false));
            return new OrderDtos.AllowedTransitions(null, null, null, order.getRowVersion(), actions);
        }
        var previous = OrderTransitionService.parse(order.getStatusBeforeRefund());
        var facts = OrderStateMachine.Facts.of(order);
        var list = new ArrayList<OrderDtos.AllowedTransition>();
        for (var edge : OrderStateMachine.transitionsFrom(order.getStatus(), facts)) {
            if (!edge.actors().contains(actor.type())) continue;
            list.add(new OrderDtos.AllowedTransition(edge.to().name(), action(order.getStatus(), edge.to()),
                edge.reasonRequired() || actor.type() == OrderActorType.ADMIN, edge.shippingRequired()));
        }
        if (OrderStateMachine.canCollectCod(order.getStatus(), actor.type(), facts)) {
            list.add(new OrderDtos.AllowedTransition(null, "COLLECT_COD", actor.type() == OrderActorType.ADMIN, false));
        }
        if (order.getStatus() == OrderStatus.REFUND_REQUESTED && previous != null
            && OrderStateMachine.REJECT_REFUND_ACTORS.contains(actor.type())) {
            list.add(new OrderDtos.AllowedTransition(previous.name(), "REJECT_REFUND", true, false));
        }
        return new OrderDtos.AllowedTransitions(order.getStatus().name(), order.getPaymentStatus().name(),
            order.getFulfillmentStatus().name(), order.getRowVersion(), list);
    }

    Order load(OrderActor actor, UUID orderId) {
        return orders.findById(orderId).filter(actor::canSee)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์"));
    }

    static String action(OrderStatus from, OrderStatus to) {
        return switch (to) {
            case PAID_AWAITING_MERCHANT_CONFIRM -> "MARK_PAID";
            case MERCHANT_CONFIRMED -> "CONFIRM";
            case READY_TO_PACK, PACKING, PACKED -> "PROCESS";
            case DISPATCHED -> "SHIP";
            case DELIVERED -> "DELIVER";
            case COMPLETED -> "COMPLETE";
            case CANCELLED -> "CANCEL";
            case REFUND_REQUESTED -> "REQUEST_REFUND";
            case REFUNDED -> "APPROVE_REFUND";
            default -> from == OrderStatus.REFUND_REQUESTED ? "REJECT_REFUND" : to.name();
        };
    }

    public OrderDtos.OrderDetail detail(Order order, OrderActor actor) {
        var shop = shops.findById(order.getShopId()).orElse(null);
        if (MerchantVisibility.restricted(order, actor)) return restrictedDetail(order, shop);
        boolean staff = actor.type() != OrderActorType.BUYER;
        boolean merchant = actor.type() == OrderActorType.MERCHANT;
        var shipment = shipments.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId())
            .map(s -> new OrderDtos.ShipmentView(s.getStatus(), s.getCarrierCode(), s.getTrackingNumber(), s.getPackedAt(), s.getDispatchedAt(), s.getDeliveredAt()))
            .orElse(null);
        var address = order.getAddresses().stream().filter(a -> "SHIPPING".equals(a.getAddressType())).findFirst()
            .map(a -> address(a, masked(order, actor, shop))).orElse(null);
        boolean itemsHidden = MerchantVisibility.itemsHidden(order, actor);
        var items = itemsHidden ? null : order.getItems().stream().map(item -> new OrderDtos.Item(item.getId(), item.getProductId(), item.getSkuId(),
            item.getProductNameSnapshot(), item.getOptionLabelSnapshot(), item.getSkuCodeSnapshot(), item.getBarcodeSnapshot(),
            item.getImageUrlSnapshot(), item.getUnitPrice(), item.getCompareAtPriceSnapshot(), staff ? item.getCostSnapshot() : null,
            item.getQuantity(), item.getDiscountAmount(), item.getLineTotal(), item.getConfiguration())).toList();
        var previous = OrderTransitionService.parse(order.getStatusBeforeRefund());
        var allowed = actor.type() == OrderActorType.SYSTEM ? List.<String>of()
            : OrderStateMachine.allowedTargets(order.getStatus(), actor.type(), previous, OrderStateMachine.Facts.of(order)).stream().map(Enum::name).toList();
        var tags = merchant ? tagService.tagsByOrderIds(List.of(order.getId())).getOrDefault(order.getId(), List.of()) : null;
        var internalNote = merchant ? order.getInternalNote() : null;
        return new OrderDtos.OrderDetail(order.getId(), order.getOrderNumber(), shop == null ? null : shop.getId(),
            shop == null ? null : shop.getName(), order.getChannel(), order.getStatus().name(), order.getPaymentStatus().name(),
            order.getFulfillmentStatus().name(), order.getMerchantConfirmStatus(), order.getCurrency(), order.getSubtotal(),
            order.getDiscountTotal(), order.getShippingFee(), order.getTaxTotal(), order.getGrandTotal(), order.getCouponCode(),
            new OrderDtos.ShippingSnapshot(order.getShippingMethodId(), order.getShippingMethodNameSnapshot(), order.getShippingCarrierCodeSnapshot()),
            shipment, address, order.getBuyerNote(), staff ? order.getBuyerUserId() : null, staff ? order.getShopCustomerId() : null,
            order.getCancelReason(), order.getStatusBeforeRefund(), order.getPlacedAt(), order.getPaidAt(), order.getMerchantConfirmedAt(),
            order.getCancelledAt(), order.getCompletedAt(), order.getRowVersion(), items, allowed,
            itemsHidden, order.getItems().size(), order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
            ledger.captured(order.getId()), ledger.refunded(order.getId()), ledger.refundable(order.getId()),
            merchant ? Boolean.FALSE : null,
            order.getPaymentMethod().name(), order.getCodFee(), order.getTermsAcceptedAt(),
            internalNote, tags);
    }

    /** Merchant, not accepted yet: id, number, created time, version, accept / decline only (MerchantVisibility). */
    private OrderDtos.OrderDetail restrictedDetail(Order order, Shop shop) {
        var allowed = order.getStatus() == OrderStatus.PENDING_PAYMENT ? List.of(OrderStatus.CANCELLED.name()) : List.<String>of();
        return new OrderDtos.OrderDetail(order.getId(), order.getOrderNumber(), shop == null ? null : shop.getId(),
            shop == null ? null : shop.getName(), null, null, null, null, "AWAITING", null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null,
            order.getPlacedAt(), null, null, null, null, order.getRowVersion(), null, allowed,
            true, null, null, null, null, null, Boolean.TRUE, null, null, null, null, null);
    }

    /** Merchants see the full address only after confirming the order and with SHIPMENT_ADDRESS_READ (V1 design: reveal after confirm). */
    private boolean masked(Order order, OrderActor actor, Shop shop) {
        if (actor.type() != OrderActorType.MERCHANT) return false;
        if (order.getMerchantConfirmedAt() == null) return true;
        if (shop == null || actor.userId() == null) return true;
        return authz.checkShop(actor.userId(), shop.getId(), "SHIPMENT_ADDRESS_READ") != AuthorizationService.ShopDecision.GRANTED;
    }

    private static OrderDtos.AddressView address(OrderAddress a, boolean masked) {
        if (masked) {
            return new OrderDtos.AddressView(null, null, null, null, null, a.getDistrict(), a.getProvince(), a.getPostalCode(), a.getCountryCode(), true);
        }
        return new OrderDtos.AddressView(a.getRecipientName(), a.getPhone(), a.getAddressLine1(), a.getAddressLine2(), a.getSubdistrict(),
            a.getDistrict(), a.getProvince(), a.getPostalCode(), a.getCountryCode(), false);
    }

    OrderDtos.OrderSummary summary(Order order, Shop shop, OrderActor actor,
                                   List<OrderDtos.TagView> tags, Shipment shipment) {
        return summary(order, shop, actor, tags, shipment, null, null);
    }

    OrderDtos.OrderSummary summary(Order order, Shop shop, OrderActor actor,
                                   List<OrderDtos.TagView> tags, Shipment shipment,
                                   String customerName, String customerKey) {
        boolean merchant = actor.type() == OrderActorType.MERCHANT;
        if (MerchantVisibility.restricted(order, actor)) {
            return new OrderDtos.OrderSummary(order.getId(), order.getOrderNumber(), shop == null ? null : shop.getId(),
                shop == null ? null : shop.getName(), null, null, null, null, null, null, null, order.getPlacedAt(), null,
                order.getRowVersion(), true, null, null, null, null, null, null, null);
        }
        int qty = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        List<OrderDtos.OrderLinePreview> previews = order.getItems().isEmpty() ? null
            : order.getItems().stream()
                .map(item -> new OrderDtos.OrderLinePreview(
                    item.getProductNameSnapshot(),
                    item.getOptionLabelSnapshot(),
                    item.getImageUrlSnapshot(),
                    item.getQuantity()))
                .toList();
        return new OrderDtos.OrderSummary(order.getId(), order.getOrderNumber(), shop == null ? null : shop.getId(),
            shop == null ? null : shop.getName(), order.getStatus().name(), order.getPaymentStatus().name(),
            order.getFulfillmentStatus().name(), order.getGrandTotal(), order.getCurrency(), order.getItems().size(), qty,
            order.getPlacedAt(), order.getPaidAt(), order.getRowVersion(), false, order.getPaymentMethod().name(),
            merchant ? (tags == null ? List.of() : tags) : null,
            shipment == null ? null : shipment.getCarrierCode(),
            shipment == null ? null : shipment.getTrackingNumber(),
            merchant ? customerName : null,
            merchant ? customerKey : null,
            previews);
    }

    private Map<UUID, String> recipientNames(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Map.of();
        var map = new HashMap<UUID, String>();
        for (Object[] row : orderAddresses.recipientNamesByOrderIds(orderIds)) {
            UUID orderId = (UUID) row[0];
            String name = (String) row[1];
            if (orderId != null && name != null && !name.isBlank()) map.putIfAbsent(orderId, name);
        }
        return map;
    }

    private Map<UUID, Shipment> latestShipments(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Map.of();
        var map = new java.util.HashMap<UUID, Shipment>();
        for (var shipment : shipments.findByOrderIdIn(orderIds)) {
            var prior = map.get(shipment.getOrderId());
            if (prior == null || prior.getCreatedDateTime().isBefore(shipment.getCreatedDateTime())) {
                map.put(shipment.getOrderId(), shipment);
            }
        }
        return map;
    }

    private Map<UUID, Shop> shopMap(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return shops.findAllById(ids.stream().distinct().toList()).stream().collect(Collectors.toMap(Shop::getUuid, Function.identity()));
    }

    private static <T> T parseEnum(String raw, String field, Function<String, T> parser, List<FieldViolation> violations) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return parser.apply(raw);
        } catch (IllegalArgumentException exception) {
            violations.add(new FieldViolation(field, "INVALID", "ค่า " + field + " ไม่ถูกต้อง"));
            return null;
        }
    }

    private static LocalDate parseDate(String raw, String field, List<FieldViolation> violations) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            violations.add(new FieldViolation(field, "INVALID", field + " ต้องเป็นรูปแบบ YYYY-MM-DD"));
            return null;
        }
    }

    static Instant now() { return Instant.now(); }
}
