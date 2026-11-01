package com.thaishopfun.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Public order tracking (no login): request + a PII-masked status view. No ids, slips, notes or shop owner contact. */
public final class PublicOrderDtos {
    private PublicOrderDtos() {}

    /** contact = the shipping phone or the buyer account e-mail. */
    public record LookupRequest(String orderNumber, String contact) {}

    public record PublicOrderStatusView(String orderNumber, String shopName, Instant placedAt, String status, String paymentStatus,
                                        String paymentMethod, String fulfillmentStatus, BigDecimal grandTotal, String currency,
                                        Instant paidAt, Instant merchantConfirmedAt, Instant cancelledAt, Instant completedAt,
                                        Recipient recipient, List<Item> items, Shipment shipment) {}

    /** Masked: name "S***", phone "08x-xxx-1234", only province + postal code of the address. */
    public record Recipient(String name, String phone, String province, String postalCode) {}

    public record Item(String productName, String optionLabel, String imageUrl, int quantity, BigDecimal lineTotal) {}

    public record Shipment(String status, String carrierCode, String carrierName, String trackingNumber, String trackingUrl,
                           Instant packedAt, Instant shippedAt, Instant deliveredAt, List<Event> events) {}

    /** Timeline entry without actor / note / raw payload. */
    public record Event(Long id, String eventType, String status, String location, Instant eventAt) {}
}