package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * Public order tracking: order number + shipping phone (order_addresses.phone) or buyer e-mail (users.email).
 * Unknown number and wrong contact give the same 404 ORDER_NOT_FOUND; failures are rate limited.
 */
@Service
public class PublicOrderLookupService {
    private final OrderRepository orders;
    private final UserAccountRepository users;
    private final OrderQueryService queries;
    private final ShipmentService shipments;
    private final PublicOrderLookupRateLimiter limiter;
    private final ShipmentRepository shipmentRepository;

    public PublicOrderLookupService(OrderRepository orders, UserAccountRepository users, OrderQueryService queries,
                                    ShipmentService shipments, PublicOrderLookupRateLimiter limiter,
                                    ShipmentRepository shipmentRepository) {
        this.orders = orders;
        this.users = users;
        this.queries = queries;
        this.shipments = shipments;
        this.limiter = limiter;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional(readOnly = true)
    public PublicOrderDtos.PublicOrderStatusView lookup(PublicOrderDtos.LookupRequest request, String clientIp) {
        var number = normalizeOrderNumber(request == null ? null : request.orderNumber());
        var contact = request == null ? null : request.contact();
        var ip = clientIp == null ? "unknown" : clientIp;
        limiter.check(ip, number);
        if (number == null || contact == null || contact.isBlank() || contact.length() > 255) {
            limiter.fail(ip, number);
            throw notFound();
        }
        var order = orders.findFirstByOrderNumber(number).orElse(null);
        boolean ok = order != null && matches(order, contact);
        if (!ok) {
            limiter.fail(ip, number);
            throw notFound();
        }
        return view(order);
    }

    private boolean matches(Order order, String contact) {
        if (contact.contains("@")) {
            var email = normalizeEmail(contact);
            var stored = order.getBuyerUserId() == null ? null
                : users.findById(order.getBuyerUserId()).map(u -> u.getEmail()).orElse(null);
            return same(email, stored == null ? null : normalizeEmail(stored));
        }
        var phone = normalizePhone(contact);
        boolean hit = false;
        for (var a : order.getAddresses()) {
            // check every address (no early exit) so timing does not depend on which one matched
            hit |= same(phone, normalizePhone(a.getPhone()));
        }
        return hit;
    }

    private PublicOrderDtos.PublicOrderStatusView view(Order order) {
        var detail = queries.detail(order, OrderActor.system());
        var address = order.getAddresses().stream().filter(a -> "SHIPPING".equals(a.getAddressType())).findFirst().orElse(null);
        var recipient = address == null ? null : new PublicOrderDtos.Recipient(maskName(address.getRecipientName()),
            maskPhone(address.getPhone()), address.getProvince(), address.getPostalCode());
        var items = detail.items() == null ? List.<PublicOrderDtos.Item>of() : detail.items().stream()
            .map(i -> new PublicOrderDtos.Item(i.productName(), i.optionLabel(), i.imageUrl(), i.quantity(), i.lineTotal())).toList();
        PublicOrderDtos.Shipment shipment = null;
        // pre-check: ShipmentService.get throws inside the shared read-only tx, which would mark it rollback-only
        if (shipmentRepository.findFirstByOrderIdOrderByCreatedDateTimeDesc(order.getId()).isPresent()) try {
            var s = shipments.get(OrderActor.system(), order.getId());
            var events = s.events() == null ? List.<PublicOrderDtos.Event>of() : s.events().stream()
                .map(e -> new PublicOrderDtos.Event(e.id(), e.eventType(), e.status(), e.location(), e.eventAt())).toList();
            shipment = new PublicOrderDtos.Shipment(s.status(), s.carrierCode(), s.carrierName(), s.trackingNumber(), s.trackingUrl(),
                s.packedAt(), s.shippedAt(), s.deliveredAt(), events);
        } catch (AuthException none) {
            // no shipment yet
        }
        return new PublicOrderDtos.PublicOrderStatusView(detail.orderNumber(), detail.shopName(), detail.placedAt(), detail.status(),
            detail.paymentStatus(), detail.paymentMethod(), detail.fulfillmentStatus(), detail.grandTotal(), detail.currency(),
            detail.paidAt(), detail.merchantConfirmedAt(), detail.cancelledAt(), detail.completedAt(), recipient, items, shipment);
    }

    static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found or contact does not match");
    }

    static String normalizeOrderNumber(String raw) {
        if (raw == null) return null;
        var value = raw.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty() || value.length() > 40 ? null : value;
    }

    static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Digits only; +66 / 66 prefix becomes 0 (0812345678). */
    static String normalizePhone(String raw) {
        if (raw == null) return null;
        var digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("66") && digits.length() == 11) digits = "0" + digits.substring(2);
        return digits.isEmpty() ? null : digits;
    }

    /** Constant-time compare; null never matches. */
    static boolean same(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String maskName(String name) {
        if (name == null || name.isBlank()) return null;
        var t = name.trim();
        return t.codePoints().limit(1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append) + "***";
    }

    static String maskPhone(String phone) {
        var d = normalizePhone(phone);
        if (d == null) return null;
        return d.length() <= 4 ? "***" : "xxx-xxx-" + d.substring(d.length() - 4);
    }
}