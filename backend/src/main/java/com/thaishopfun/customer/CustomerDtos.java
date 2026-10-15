package com.thaishopfun.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record AddressRequest(String label, String recipientName, String phone, String addressLine1, String addressLine2,
                                 String subdistrict, String district, String province, String postalCode, String countryCode,
                                 Boolean defaultShipping, Boolean defaultBilling) {}

    /**
     * Create / update body. On update, {@code addresses} null = keep addresses (manage them with the address endpoints);
     * {@code tags} null = keep tags. {@code allowDuplicate=true} saves even when another live customer of the shop has the
     * same phone/email (the response then lists them in possibleDuplicates); otherwise 409 DUPLICATE_CUSTOMER.
     */
    public record CustomerWriteRequest(String displayName, String phone, String email, String customerCode, String source,
                                       String status, String notes, List<String> tags, List<AddressRequest> addresses,
                                       Boolean allowDuplicate) {}

    public record MergeRequest(UUID primaryId, UUID secondaryId) {}

    public record AddressResponse(UUID id, String label, String recipientName, String phone, String addressLine1, String addressLine2,
                                  String subdistrict, String district, String province, String postalCode, String countryCode,
                                  boolean defaultShipping, boolean defaultBilling) {}

    public record DuplicateMatch(UUID id, String customerCode, String displayName, String phone, String email,
                                 CustomerStatus status, List<String> matchedOn) {}

    public record CustomerResponse(UUID id, String customerCode, String displayName, String phone, String email,
                                   CustomerSource source, CustomerStatus status, String notes, List<String> tags,
                                   UUID userId, UUID mergedIntoId, Instant mergedAt, List<AddressResponse> addresses,
                                   Long orderCount, Instant createdAt, Instant updatedAt, boolean piiMasked,
                                   List<DuplicateMatch> possibleDuplicates) {}

    public record DuplicateCheckResponse(String phoneNormalized, String emailNormalized, List<DuplicateMatch> matches) {}

    public record MergeResponse(CustomerResponse primary, UUID secondaryId, boolean alreadyMerged, int addressesMoved,
                                int addressesSkippedAsDuplicate, int ordersMoved, List<String> tagsAdded, List<String> fieldsFilled) {}

    public record OrderSummary(UUID id, String orderNumber, String channel, String status, String paymentStatus,
                               String fulfillmentStatus, BigDecimal grandTotal, String currency, Instant placedAt, Instant createdAt) {}

    public record OrderHistoryResponse(UUID customerId, long orderCount, BigDecimal totalSpent, List<OrderSummary> items,
                                       int page, int size, long total, int totalPages) {}

    public record DuplicateCustomerError(String code, String message, UUID existingCustomerId, List<DuplicateMatch> duplicates) {}
}
