package com.thaishopfun.customer;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shop customer records (CRM/OMS). Every method re-checks that the caller is a member of the shop (or super admin):
 * another shop's customer is indistinguishable from a missing one (404).
 */
@Service
public class CustomerService {
    static final String PII_PERMISSION = "CUSTOMER_PII_READ";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TAGS = 20;
    private static final int MAX_ADDRESSES = 20;
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final Pattern POSTAL = Pattern.compile("^\\d{5}$");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
    private static final Set<CustomerStatus> WRITABLE_STATUSES = EnumSet.of(CustomerStatus.ACTIVE, CustomerStatus.INACTIVE, CustomerStatus.BLOCKED, CustomerStatus.ARCHIVED);
    private static final Map<String, String> SORTS = Map.of(
        "createdat,desc", "c.createdDateTime desc, c.id",
        "createdat,asc", "c.createdDateTime asc, c.id",
        "updatedat,desc", "c.modifiedDateTime desc, c.id",
        "name,asc", "lower(c.displayName) asc, c.id",
        "name,desc", "lower(c.displayName) desc, c.id");
    private static final Map<String, String> MESSAGES = Map.ofEntries(
        Map.entry("DISPLAY_NAME_REQUIRED", "กรุณาระบุชื่อลูกค้า"),
        Map.entry("DISPLAY_NAME_TOO_LONG", "ชื่อลูกค้ายาวเกิน 150 ตัวอักษร"),
        Map.entry("PHONE_INVALID", "เบอร์โทรไม่ถูกต้อง (ใช้เบอร์ไทย เช่น 081-234-5678 หรือ +66 81 234 5678)"),
        Map.entry("EMAIL_INVALID", "อีเมลไม่ถูกต้อง"),
        Map.entry("CUSTOMER_CODE_INVALID", "รหัสลูกค้าใช้ได้เฉพาะ A-Z 0-9 _ - ไม่เกิน 40 ตัว"),
        Map.entry("CUSTOMER_CODE_TAKEN", "รหัสลูกค้านี้มีอยู่แล้วในร้าน"),
        Map.entry("SOURCE_INVALID", "แหล่งที่มาของลูกค้าไม่ถูกต้อง"),
        Map.entry("STATUS_INVALID", "สถานะลูกค้าไม่ถูกต้อง (ACTIVE, INACTIVE, BLOCKED, ARCHIVED)"),
        Map.entry("NOTES_TOO_LONG", "บันทึกยาวเกิน 5,000 ตัวอักษร"),
        Map.entry("TAG_LIMIT", "มีแท็กได้ไม่เกิน 20 แท็ก"),
        Map.entry("TAG_INVALID", "แท็กต้องมี 1-50 ตัวอักษร"),
        Map.entry("ADDRESS_LIMIT", "มีที่อยู่ได้ไม่เกิน 20 รายการ"),
        Map.entry("RECIPIENT_REQUIRED", "กรุณาระบุชื่อผู้รับ"),
        Map.entry("RECIPIENT_TOO_LONG", "ชื่อผู้รับยาวเกิน 150 ตัวอักษร"),
        Map.entry("ADDRESS_LINE_REQUIRED", "กรุณาระบุที่อยู่ (บ้านเลขที่ ถนน)"),
        Map.entry("ADDRESS_LINE_TOO_LONG", "ที่อยู่ยาวเกิน 255 ตัวอักษร"),
        Map.entry("LABEL_TOO_LONG", "ชื่อที่อยู่ยาวเกิน 50 ตัวอักษร"),
        Map.entry("SUBDISTRICT_TOO_LONG", "ตำบล/แขวงยาวเกิน 100 ตัวอักษร"),
        Map.entry("DISTRICT_REQUIRED", "กรุณาระบุอำเภอ/เขต"),
        Map.entry("PROVINCE_REQUIRED", "กรุณาระบุจังหวัด"),
        Map.entry("POSTAL_CODE_INVALID", "รหัสไปรษณีย์ต้องเป็นตัวเลข 5 หลัก"),
        Map.entry("COUNTRY_CODE_INVALID", "รหัสประเทศต้องเป็นตัวอักษร 2 ตัว เช่น TH"),
        Map.entry("ADDRESS_DEFAULT_DUPLICATE", "เลือกที่อยู่หลักได้ประเภทละ 1 รายการ"),
        Map.entry("SORT_INVALID", "รูปแบบการเรียงไม่ถูกต้อง (createdAt,desc | createdAt,asc | updatedAt,desc | name,asc | name,desc)"),
        Map.entry("MERGE_IDS_REQUIRED", "กรุณาระบุลูกค้าหลักและลูกค้าที่จะรวม"),
        Map.entry("MERGE_SAME_CUSTOMER", "รวมลูกค้ากับตัวเองไม่ได้"));

    private final ShopRepository shops;
    private final ShopCustomerRepository customers;
    private final ShopCustomerAddressRepository addresses;
    private final CustomerOrderRecordRepository orders;
    private final CustomerAuditLogRepository audits;
    private final AuthorizationService authorization;
    private final EntityManager entities;

    public CustomerService(ShopRepository shops, ShopCustomerRepository customers, ShopCustomerAddressRepository addresses,
                           CustomerOrderRecordRepository orders, CustomerAuditLogRepository audits,
                           AuthorizationService authorization, EntityManager entities) {
        this.shops = shops;
        this.customers = customers;
        this.addresses = addresses;
        this.orders = orders;
        this.audits = audits;
        this.authorization = authorization;
        this.entities = entities;
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CustomerDtos.CustomerResponse> list(UUID userId, Long shopNo, String q, String status, String source,
                                                                        String tag, String sort, Integer page, Integer size) {
        var shop = requireShop(userId, shopNo);
        var pii = canReadPii(userId, shopNo);
        var violations = new ArrayList<FieldViolation>();
        var statusFilter = parseEnum(CustomerStatus.class, status, "status", "STATUS_INVALID", violations);
        var sourceFilter = parseEnum(CustomerSource.class, source, "source", "SOURCE_INVALID", violations);
        var order = sort == null || sort.isBlank() ? SORTS.get("createdat,desc") : SORTS.get(sort.trim().toLowerCase(Locale.ROOT).replace(":", ","));
        if (order == null) violations.add(violation("sort", "SORT_INVALID"));
        if (!violations.isEmpty()) throw new ValidationException(violations);
        int pageNo = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);

        var where = new StringBuilder(" where c.shopId = :shopId and c.deletedAt is null");
        var params = new HashMap<String, Object>();
        params.put("shopId", shop.getUuid());
        if (statusFilter != null) {
            where.append(" and c.status = :status");
            params.put("status", statusFilter);
        } else {
            where.append(" and c.status not in (com.thaishopfun.customer.CustomerStatus.MERGED, com.thaishopfun.customer.CustomerStatus.ARCHIVED)");
        }
        if (sourceFilter != null) {
            where.append(" and c.source = :source");
            params.put("source", sourceFilter);
        }
        if (tag != null && !tag.isBlank()) {
            where.append(" and exists (select 1 from ShopCustomer t join t.tags tg where t = c and lower(tg) = :tag)");
            params.put("tag", normalizeTag(tag).toLowerCase(Locale.ROOT));
        }
        if (q != null && !q.isBlank()) {
            var text = q.trim().toLowerCase(Locale.ROOT);
            var clause = new StringBuilder("lower(c.displayName) like :q or c.emailNormalized like :q or lower(c.customerCode) like :q or lower(c.phone) like :q");
            params.put("q", "%" + escapeLike(text) + "%");
            var digits = text.replaceAll("\\D", "");
            if (digits.length() >= 3) {
                var normalized = ContactNormalizer.phone(text);
                clause.append(" or c.phoneNormalized like :qPhone");
                params.put("qPhone", "%" + (normalized != null && normalized.length() >= 9 ? normalized : digits) + "%");
            }
            where.append(" and (").append(clause).append(")");
        }
        var countQuery = entities.createQuery("select count(c) from ShopCustomer c" + where, Long.class);
        var listQuery = entities.createQuery("select c from ShopCustomer c" + where + " order by " + order, ShopCustomer.class);
        params.forEach((key, value) -> { countQuery.setParameter(key, value); listQuery.setParameter(key, value); });
        var total = countQuery.getSingleResult();
        var items = listQuery.setFirstResult(pageNo * pageSize).setMaxResults(pageSize).getResultList().stream()
            .map(customer -> toResponse(customer, pii, false, null, null)).toList();
        return new CatalogDtos.PageResponse<>(items, pageNo, pageSize, total);
    }

    @Transactional(readOnly = true)
    public CustomerDtos.CustomerResponse get(UUID userId, Long shopNo, UUID customerId) {
        var shop = requireShop(userId, shopNo);
        var customer = requireCustomer(shop, customerId);
        return toResponse(customer, canReadPii(userId, shopNo), true, orders.countByShopIdAndShopCustomerId(shop.getUuid(), customer.getId()), null);
    }

    /** Duplicate check for a phone/email pair (before create), optionally excluding one customer (before update). */
    @Transactional(readOnly = true)
    public CustomerDtos.DuplicateCheckResponse duplicates(UUID userId, Long shopNo, String phone, String email, UUID excludeId) {
        var shop = requireShop(userId, shopNo);
        var phoneKey = ContactNormalizer.phone(phone);
        var emailKey = ContactNormalizer.email(email);
        return new CustomerDtos.DuplicateCheckResponse(phoneKey, emailKey, matches(shop, phoneKey, emailKey, excludeId, canReadPii(userId, shopNo)));
    }

    /** Other live customers of the shop that share this customer's phone or email. */
    @Transactional(readOnly = true)
    public CustomerDtos.DuplicateCheckResponse duplicatesOf(UUID userId, Long shopNo, UUID customerId) {
        var shop = requireShop(userId, shopNo);
        var customer = requireCustomer(shop, customerId);
        return new CustomerDtos.DuplicateCheckResponse(customer.getPhoneNormalized(), customer.getEmailNormalized(),
            matches(shop, customer.getPhoneNormalized(), customer.getEmailNormalized(), customer.getId(), canReadPii(userId, shopNo)));
    }

    @Transactional(readOnly = true)
    public CustomerDtos.OrderHistoryResponse orders(UUID userId, Long shopNo, UUID customerId, Integer page, Integer size) {
        var shop = requireShop(userId, shopNo);
        var customer = requireCustomer(shop, customerId);
        int pageNo = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        var result = orders.findByShopIdAndShopCustomerId(shop.getUuid(), customer.getId(),
            PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc("createdDateTime"), Sort.Order.asc("id"))));
        var items = result.getContent().stream().map(o -> new CustomerDtos.OrderSummary(o.getId(), o.getOrderNumber(), o.getChannel(),
            o.getStatus(), o.getPaymentStatus(), o.getFulfillmentStatus(), o.getGrandTotal(), o.getCurrency(), o.getPlacedAt(), o.getCreatedDateTime())).toList();
        return new CustomerDtos.OrderHistoryResponse(customer.getId(), result.getTotalElements(), orders.totalSpent(shop.getUuid(), customer.getId()),
            items, pageNo, pageSize, result.getTotalElements(), result.getTotalPages());
    }

    // ---------------------------------------------------------------- writes

    @Transactional
    public CustomerDtos.CustomerResponse create(UUID userId, Long shopNo, CustomerDtos.CustomerWriteRequest request) {
        var shop = requireShop(userId, shopNo);
        var req = request == null ? emptyRequest() : request;
        var customer = new ShopCustomer(shop.getUuid());
        var violations = new ArrayList<FieldViolation>();
        applyFields(shop, customer, req, true, violations);
        var addressRequests = req.addresses() == null ? List.<CustomerDtos.AddressRequest>of() : req.addresses();
        validateAddresses(addressRequests, "addresses", violations);
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var possible = matches(shop, customer.getPhoneNormalized(), customer.getEmailNormalized(), null, true);
        if (!possible.isEmpty() && !Boolean.TRUE.equals(req.allowDuplicate())) throw new DuplicateCustomerException(maskMatches(possible, canReadPii(userId, shopNo)));
        customer.stamp(userId);
        customers.saveAndFlush(customer);
        boolean anyShipping = addressRequests.stream().anyMatch(a -> Boolean.TRUE.equals(a.defaultShipping()));
        boolean anyBilling = addressRequests.stream().anyMatch(a -> Boolean.TRUE.equals(a.defaultBilling()));
        // one request → strictly increasing created times so list order = request order (Instant.now() can tie on Windows)
        var createdBase = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        for (int i = 0; i < addressRequests.size(); i++) {
            var a = addressRequests.get(i);
            var address = new ShopCustomerAddress(customer.getId());
            address.initCreatedDateTime(createdBase.plus(i, java.time.temporal.ChronoUnit.MICROS));
            applyAddress(address, a);
            address.setDefaultShipping(anyShipping ? Boolean.TRUE.equals(a.defaultShipping()) : i == 0);
            address.setDefaultBilling(anyBilling ? Boolean.TRUE.equals(a.defaultBilling()) : i == 0);
            address.stamp(userId);
            addresses.save(address);
        }
        audit(userId, shop, "CUSTOMER_CREATE", "CUSTOMER_CREATE", customer.getId(),
            possible.isEmpty() ? Map.of() : Map.of("possibleDuplicateIds", possible.stream().map(m -> m.id().toString()).toList()));
        return toResponse(customer, canReadPii(userId, shopNo), true, 0L, maskMatches(possible, canReadPii(userId, shopNo)));
    }

    @Transactional
    public CustomerDtos.CustomerResponse update(UUID userId, Long shopNo, UUID customerId, CustomerDtos.CustomerWriteRequest request) {
        var shop = requireShop(userId, shopNo);
        var customer = requireWritable(shop, customerId);
        var req = request == null ? emptyRequest() : request;
        var oldPhone = customer.getPhoneNormalized();
        var oldEmail = customer.getEmailNormalized();
        var violations = new ArrayList<FieldViolation>();
        applyFields(shop, customer, req, false, violations);
        if (req.addresses() != null) validateAddresses(req.addresses(), "addresses", violations);
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var contactChanged = !Objects.equals(oldPhone, customer.getPhoneNormalized()) || !Objects.equals(oldEmail, customer.getEmailNormalized());
        var possible = contactChanged ? matches(shop, customer.getPhoneNormalized(), customer.getEmailNormalized(), customer.getId(), true) : List.<CustomerDtos.DuplicateMatch>of();
        if (!possible.isEmpty() && !Boolean.TRUE.equals(req.allowDuplicate())) {
            throw new DuplicateCustomerException(maskMatches(possible, canReadPii(userId, shopNo)));
        }
        if (req.addresses() != null) replaceAddresses(userId, customer, req.addresses());
        customer.stamp(userId);
        customers.saveAndFlush(customer);
        audit(userId, shop, "CUSTOMER_UPDATE", "CUSTOMER_UPDATE", customer.getId(), Map.of());
        return toResponse(customer, canReadPii(userId, shopNo), true, orders.countByShopIdAndShopCustomerId(shop.getUuid(), customer.getId()),
            maskMatches(possible, canReadPii(userId, shopNo)));
    }

    /** Soft delete: status ARCHIVED (row, addresses and order links stay). Idempotent. */
    @Transactional
    public CustomerDtos.CustomerResponse archive(UUID userId, Long shopNo, UUID customerId) {
        var shop = requireShop(userId, shopNo);
        var customer = requireCustomer(shop, customerId);
        if (customer.getStatus() == CustomerStatus.MERGED) throw merged(customer);
        if (customer.getStatus() != CustomerStatus.ARCHIVED) {
            customer.setStatus(CustomerStatus.ARCHIVED);
            customer.stamp(userId);
            customers.saveAndFlush(customer);
            audit(userId, shop, "CUSTOMER_ARCHIVE", "CUSTOMER_UPDATE", customer.getId(), Map.of());
        }
        return toResponse(customer, canReadPii(userId, shopNo), true, orders.countByShopIdAndShopCustomerId(shop.getUuid(), customer.getId()), null);
    }

    @Transactional
    public CustomerDtos.CustomerResponse addAddress(UUID userId, Long shopNo, UUID customerId, CustomerDtos.AddressRequest request) {
        var shop = requireShop(userId, shopNo);
        var customer = requireWritable(shop, customerId);
        var current = addresses.findByCustomerIdAndDeletedAtIsNullOrderByCreatedDateTimeAscIdAsc(customer.getId());
        var violations = new ArrayList<FieldViolation>();
        if (current.size() >= MAX_ADDRESSES) violations.add(violation("addresses", "ADDRESS_LIMIT"));
        validateAddress(request, "", violations);
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var address = new ShopCustomerAddress(customer.getId());
        applyAddress(address, request);
        boolean shipping = Boolean.TRUE.equals(request.defaultShipping()) || current.stream().noneMatch(ShopCustomerAddress::isDefaultShipping);
        boolean billing = Boolean.TRUE.equals(request.defaultBilling()) || current.stream().noneMatch(ShopCustomerAddress::isDefaultBilling);
        clearDefaults(current, shipping, billing);
        address.setDefaultShipping(shipping);
        address.setDefaultBilling(billing);
        address.stamp(userId);
        addresses.saveAndFlush(address);
        touch(userId, customer);
        audit(userId, shop, "CUSTOMER_ADDRESS_ADD", "CUSTOMER_UPDATE", customer.getId(), Map.of("addressId", address.getId().toString()));
        return get(userId, shopNo, customerId);
    }

    @Transactional
    public CustomerDtos.CustomerResponse updateAddress(UUID userId, Long shopNo, UUID customerId, UUID addressId, CustomerDtos.AddressRequest request) {
        var shop = requireShop(userId, shopNo);
        var customer = requireWritable(shop, customerId);
        var address = addresses.findByIdAndCustomerIdAndDeletedAtIsNull(addressId, customer.getId()).orElseThrow(CustomerService::addressNotFound);
        var violations = new ArrayList<FieldViolation>();
        validateAddress(request, "", violations);
        if (!violations.isEmpty()) throw new ValidationException(violations);
        applyAddress(address, request);
        var others = addresses.findByCustomerIdAndDeletedAtIsNullOrderByCreatedDateTimeAscIdAsc(customer.getId()).stream()
            .filter(a -> !a.getId().equals(address.getId())).toList();
        if (request.defaultShipping() != null) {
            if (request.defaultShipping()) clearDefaults(others, true, false);
            address.setDefaultShipping(request.defaultShipping());
        }
        if (request.defaultBilling() != null) {
            if (request.defaultBilling()) clearDefaults(others, false, true);
            address.setDefaultBilling(request.defaultBilling());
        }
        address.stamp(userId);
        addresses.saveAndFlush(address);
        touch(userId, customer);
        audit(userId, shop, "CUSTOMER_ADDRESS_UPDATE", "CUSTOMER_UPDATE", customer.getId(), Map.of("addressId", address.getId().toString()));
        return get(userId, shopNo, customerId);
    }

    @Transactional
    public CustomerDtos.CustomerResponse deleteAddress(UUID userId, Long shopNo, UUID customerId, UUID addressId) {
        var shop = requireShop(userId, shopNo);
        var customer = requireWritable(shop, customerId);
        var address = addresses.findByIdAndCustomerIdAndDeletedAtIsNull(addressId, customer.getId()).orElseThrow(CustomerService::addressNotFound);
        boolean wasShipping = address.isDefaultShipping();
        boolean wasBilling = address.isDefaultBilling();
        address.softDelete(Instant.now());
        address.stamp(userId);
        addresses.saveAndFlush(address);
        var remaining = addresses.findByCustomerIdAndDeletedAtIsNullOrderByCreatedDateTimeAscIdAsc(customer.getId());
        if (!remaining.isEmpty()) {
            if (wasShipping) remaining.get(0).setDefaultShipping(true);
            if (wasBilling) remaining.get(0).setDefaultBilling(true);
            addresses.saveAllAndFlush(remaining);
        }
        touch(userId, customer);
        audit(userId, shop, "CUSTOMER_ADDRESS_DELETE", "CUSTOMER_UPDATE", customer.getId(), Map.of("addressId", address.getId().toString()));
        return get(userId, shopNo, customerId);
    }

    /**
     * Merge {@code secondaryId} into {@code primaryId} (same shop). Rules:
     * primary keeps its own values and only empty fields are filled from the secondary; tags are unioned; notes are
     * concatenated under a header; secondary addresses move to the primary unless the same address already exists
     * (those are soft-deleted); orders and earlier merges are re-pointed to the primary; the secondary becomes MERGED
     * with merged_into_id (never hard-deleted); one CUSTOMER_MERGE audit row. Repeating the same merge is a no-op.
     */
    @Transactional
    public CustomerDtos.MergeResponse merge(UUID userId, Long shopNo, CustomerDtos.MergeRequest request) {
        var shop = requireShop(userId, shopNo);
        var violations = new ArrayList<FieldViolation>();
        if (request == null || request.primaryId() == null) violations.add(violation("primaryId", "MERGE_IDS_REQUIRED"));
        if (request == null || request.secondaryId() == null) violations.add(violation("secondaryId", "MERGE_IDS_REQUIRED"));
        if (violations.isEmpty() && request.primaryId().equals(request.secondaryId())) violations.add(violation("secondaryId", "MERGE_SAME_CUSTOMER"));
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var pii = canReadPii(userId, shopNo);

        // lock both rows in a stable order so concurrent merges cannot deadlock or double-apply
        var first = request.primaryId().compareTo(request.secondaryId()) < 0 ? request.primaryId() : request.secondaryId();
        var second = first.equals(request.primaryId()) ? request.secondaryId() : request.primaryId();
        var lockedFirst = customers.lockInShop(first, shop.getUuid()).orElseThrow(CustomerService::customerNotFound);
        var lockedSecond = customers.lockInShop(second, shop.getUuid()).orElseThrow(CustomerService::customerNotFound);
        var primary = lockedFirst.getId().equals(request.primaryId()) ? lockedFirst : lockedSecond;
        var secondary = primary == lockedFirst ? lockedSecond : lockedFirst;

        if (secondary.getStatus() == CustomerStatus.MERGED) {
            if (primary.getId().equals(secondary.getMergedIntoId())) {
                return new CustomerDtos.MergeResponse(toResponse(primary, pii, true, orders.countByShopIdAndShopCustomerId(shop.getUuid(), primary.getId()), null),
                    secondary.getId(), true, 0, 0, 0, List.of(), List.of());
            }
            throw new AuthException(HttpStatus.CONFLICT, "CUSTOMER_ALREADY_MERGED", "ลูกค้ารายนี้ถูกรวมเข้ากับลูกค้าอื่นไปแล้ว");
        }
        if (primary.getStatus() == CustomerStatus.MERGED) {
            throw new AuthException(HttpStatus.CONFLICT, "MERGE_TARGET_MERGED", "ลูกค้าหลักถูกรวมเข้ากับลูกค้าอื่นไปแล้ว ให้รวมเข้ากับลูกค้ารายนั้นแทน");
        }
        if (primary.getUserId() != null && secondary.getUserId() != null && !primary.getUserId().equals(secondary.getUserId())) {
            throw new AuthException(HttpStatus.CONFLICT, "MERGE_ACCOUNT_CONFLICT", "ลูกค้าทั้งสองผูกกับบัญชีผู้ใช้คนละบัญชี รวมกันไม่ได้");
        }

        var matchedOn = matchedOn(primary, secondary);
        var filled = new ArrayList<String>();
        // unique keys (customer_code, user_id) must leave the secondary before the primary takes them
        String movedCode = null;
        UUID movedUser = null;
        if (blank(primary.getCustomerCode()) && !blank(secondary.getCustomerCode())) { movedCode = secondary.getCustomerCode(); secondary.setCustomerCode(null); }
        if (primary.getUserId() == null && secondary.getUserId() != null) { movedUser = secondary.getUserId(); secondary.setUserId(null); }
        if (movedCode != null || movedUser != null) customers.saveAndFlush(secondary);
        if (movedCode != null) { primary.setCustomerCode(movedCode); filled.add("customerCode"); }
        if (movedUser != null) { primary.setUserId(movedUser); filled.add("userId"); }
        if (blank(primary.getPhone()) && !blank(secondary.getPhone())) { primary.setPhone(secondary.getPhone(), secondary.getPhoneNormalized()); filled.add("phone"); }
        if (blank(primary.getEmail()) && !blank(secondary.getEmail())) { primary.setEmail(secondary.getEmail(), secondary.getEmailNormalized()); filled.add("email"); }
        if (blank(primary.getDisplayName()) && !blank(secondary.getDisplayName())) { primary.setDisplayName(secondary.getDisplayName()); filled.add("displayName"); }

        if (!blank(secondary.getNotes())) {
            var header = "--- รวมจากลูกค้า " + secondary.getDisplayName()
                + (blank(secondary.getCustomerCode()) && movedCode == null ? "" : " (" + (movedCode != null ? movedCode : secondary.getCustomerCode()) + ")")
                + " เมื่อ " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Bangkok")).format(Instant.now()) + " ---";
            var merged = blank(primary.getNotes()) ? header + "\n" + secondary.getNotes().trim() : primary.getNotes().trim() + "\n\n" + header + "\n" + secondary.getNotes().trim();
            primary.setNotes(merged.length() > 20000 ? merged.substring(0, 20000) : merged);
            filled.add("notes");
        }

        var existingTags = primary.getTags().stream().map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        var tagsAdded = new ArrayList<String>();
        for (var tag : secondary.getTags()) {
            if (existingTags.add(tag.toLowerCase(Locale.ROOT))) { primary.getTags().add(tag); tagsAdded.add(tag); }
        }

        var primaryAddresses = addresses.findByCustomerIdAndDeletedAtIsNullOrderByCreatedDateTimeAscIdAsc(primary.getId());
        var keys = primaryAddresses.stream().map(CustomerService::addressKey).collect(Collectors.toSet());
        boolean hasShipping = primaryAddresses.stream().anyMatch(ShopCustomerAddress::isDefaultShipping);
        boolean hasBilling = primaryAddresses.stream().anyMatch(ShopCustomerAddress::isDefaultBilling);
        int moved = 0, skipped = 0;
        var now = Instant.now();
        for (var address : addresses.findByCustomerIdAndDeletedAtIsNullOrderByCreatedDateTimeAscIdAsc(secondary.getId())) {
            if (!keys.add(addressKey(address))) {
                address.softDelete(now);
                skipped++;
            } else {
                if (hasShipping) address.setDefaultShipping(false); else if (address.isDefaultShipping()) hasShipping = true;
                if (hasBilling) address.setDefaultBilling(false); else if (address.isDefaultBilling()) hasBilling = true;
                address.moveTo(primary.getId());
                moved++;
            }
            address.stamp(userId);
            addresses.saveAndFlush(address);
        }

        int ordersMoved = orders.repoint(shop.getUuid(), secondary.getId(), primary.getId());
        for (var earlier : customers.findByMergedIntoId(secondary.getId())) {
            earlier.markMerged(primary.getId(), earlier.getMergedAt());
            customers.save(earlier);
        }

        secondary.markMerged(primary.getId(), now);
        secondary.stamp(userId);
        primary.stamp(userId);
        customers.save(secondary);
        customers.saveAndFlush(primary);

        var data = new LinkedHashMap<String, Object>();
        data.put("primaryId", primary.getId().toString());
        data.put("secondaryId", secondary.getId().toString());
        data.put("matchedOn", matchedOn);
        data.put("fieldsFilled", filled);
        data.put("tagsAdded", tagsAdded);
        data.put("addressesMoved", moved);
        data.put("addressesSkippedAsDuplicate", skipped);
        data.put("ordersMoved", ordersMoved);
        audit(userId, shop, "CUSTOMER_MERGE", "CUSTOMER_UPDATE", primary.getId(), data);

        return new CustomerDtos.MergeResponse(toResponse(primary, pii, true, orders.countByShopIdAndShopCustomerId(shop.getUuid(), primary.getId()), null),
            secondary.getId(), false, moved, skipped, ordersMoved, tagsAdded, filled);
    }

    // ---------------------------------------------------------------- helpers

    private void applyFields(Shop shop, ShopCustomer customer, CustomerDtos.CustomerWriteRequest req, boolean creating, List<FieldViolation> violations) {
        var name = trimToNull(req.displayName());
        if (name == null) violations.add(violation("displayName", "DISPLAY_NAME_REQUIRED"));
        else if (name.length() > 150) violations.add(violation("displayName", "DISPLAY_NAME_TOO_LONG"));
        else customer.setDisplayName(name);

        var phone = trimToNull(req.phone());
        var phoneKey = ContactNormalizer.phone(phone);
        if (phone != null && (phone.length() > 30 || !ContactNormalizer.isValidPhone(phoneKey))) violations.add(violation("phone", "PHONE_INVALID"));
        else customer.setPhone(phone, phoneKey);

        var email = trimToNull(req.email());
        var emailKey = ContactNormalizer.email(email);
        if (email != null && !ContactNormalizer.isValidEmail(emailKey)) violations.add(violation("email", "EMAIL_INVALID"));
        else customer.setEmail(email, emailKey);

        var code = trimToNull(req.customerCode());
        if (code != null && !CODE.matcher(code).matches()) violations.add(violation("customerCode", "CUSTOMER_CODE_INVALID"));
        else if (code != null && customers.existsByShopIdAndCustomerCodeIgnoreCaseAndIdNot(shop.getUuid(), code, customer.getId())) violations.add(violation("customerCode", "CUSTOMER_CODE_TAKEN"));
        else customer.setCustomerCode(code);

        if (req.source() != null || creating) {
            var source = req.source() == null || req.source().isBlank() ? CustomerSource.MANUAL : parseEnum(CustomerSource.class, req.source(), "source", "SOURCE_INVALID", violations);
            if (source != null) customer.setSource(source);
        }
        if (req.status() != null || creating) {
            var status = req.status() == null || req.status().isBlank() ? CustomerStatus.ACTIVE : parseEnum(CustomerStatus.class, req.status(), "status", "STATUS_INVALID", violations);
            if (status != null && !WRITABLE_STATUSES.contains(status)) violations.add(violation("status", "STATUS_INVALID"));
            else if (status != null) customer.setStatus(status);
        }

        var notes = trimToNull(req.notes());
        if (notes != null && notes.length() > 5000) violations.add(violation("notes", "NOTES_TOO_LONG"));
        else if (req.notes() != null || creating) customer.setNotes(notes);

        if (req.tags() != null) {
            var clean = new LinkedHashMap<String, String>();
            for (int i = 0; i < req.tags().size(); i++) {
                var tag = normalizeTag(req.tags().get(i));
                if (tag.isEmpty() || tag.length() > 50) { violations.add(violation("tags." + i, "TAG_INVALID")); continue; }
                clean.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
            }
            if (clean.size() > MAX_TAGS) violations.add(violation("tags", "TAG_LIMIT"));
            else {
                customer.getTags().clear();
                customer.getTags().addAll(clean.values());
            }
        }
    }

    private void validateAddresses(List<CustomerDtos.AddressRequest> list, String path, List<FieldViolation> violations) {
        if (list.size() > MAX_ADDRESSES) violations.add(violation(path, "ADDRESS_LIMIT"));
        for (int i = 0; i < list.size(); i++) validateAddress(list.get(i), path + "." + i + ".", violations);
        if (list.stream().filter(a -> a != null && Boolean.TRUE.equals(a.defaultShipping())).count() > 1) violations.add(violation(path, "ADDRESS_DEFAULT_DUPLICATE"));
        if (list.stream().filter(a -> a != null && Boolean.TRUE.equals(a.defaultBilling())).count() > 1) violations.add(violation(path, "ADDRESS_DEFAULT_DUPLICATE"));
    }

    private static void validateAddress(CustomerDtos.AddressRequest a, String prefix, List<FieldViolation> violations) {
        if (a == null) { violations.add(violation(prefix + "recipientName", "RECIPIENT_REQUIRED")); return; }
        var recipient = trimToNull(a.recipientName());
        if (recipient == null) violations.add(violation(prefix + "recipientName", "RECIPIENT_REQUIRED"));
        else if (recipient.length() > 150) violations.add(violation(prefix + "recipientName", "RECIPIENT_TOO_LONG"));
        var line1 = trimToNull(a.addressLine1());
        if (line1 == null) violations.add(violation(prefix + "addressLine1", "ADDRESS_LINE_REQUIRED"));
        else if (line1.length() > 255) violations.add(violation(prefix + "addressLine1", "ADDRESS_LINE_TOO_LONG"));
        if (trimToNull(a.addressLine2()) != null && a.addressLine2().trim().length() > 255) violations.add(violation(prefix + "addressLine2", "ADDRESS_LINE_TOO_LONG"));
        if (trimToNull(a.label()) != null && a.label().trim().length() > 50) violations.add(violation(prefix + "label", "LABEL_TOO_LONG"));
        if (trimToNull(a.subdistrict()) != null && a.subdistrict().trim().length() > 100) violations.add(violation(prefix + "subdistrict", "SUBDISTRICT_TOO_LONG"));
        var district = trimToNull(a.district());
        if (district == null || district.length() > 100) violations.add(violation(prefix + "district", "DISTRICT_REQUIRED"));
        var province = trimToNull(a.province());
        if (province == null || province.length() > 100) violations.add(violation(prefix + "province", "PROVINCE_REQUIRED"));
        var postal = trimToNull(a.postalCode());
        if (postal == null || !POSTAL.matcher(postal).matches()) violations.add(violation(prefix + "postalCode", "POSTAL_CODE_INVALID"));
        var phone = trimToNull(a.phone());
        if (phone != null && (phone.length() > 30 || !ContactNormalizer.isValidPhone(ContactNormalizer.phone(phone)))) violations.add(violation(prefix + "phone", "PHONE_INVALID"));
        var country = trimToNull(a.countryCode());
        if (country != null && !COUNTRY.matcher(country.toUpperCase(Locale.ROOT)).matches()) violations.add(violation(prefix + "countryCode", "COUNTRY_CODE_INVALID"));
    }

    private static void applyAddress(ShopCustomerAddress address, CustomerDtos.AddressRequest a) {
        var country = trimToNull(a.countryCode());
        address.apply(trimToNull(a.label()), trimToNull(a.recipientName()), trimToNull(a.phone()), trimToNull(a.addressLine1()),
            trimToNull(a.addressLine2()), trimToNull(a.subdistrict()), trimToNull(a.district()), trimToNull(a.province()),
            trimToNull(a.postalCode()), country == null ? "TH" : country.toUpperCase(Locale.ROOT));
    }

    private void replaceAddresses(UUID userId, ShopCustomer customer, List<CustomerDtos.AddressRequest> requested) {
        var now = Instant.now();
        for (var old : addresses.findByCustomerIdAndDeletedAtIsNullOrderByCreatedDateTimeAscIdAsc(customer.getId())) {
            old.softDelete(now);
            old.stamp(userId);
            addresses.saveAndFlush(old);
        }
        boolean anyShipping = requested.stream().anyMatch(a -> Boolean.TRUE.equals(a.defaultShipping()));
        boolean anyBilling = requested.stream().anyMatch(a -> Boolean.TRUE.equals(a.defaultBilling()));
        // strictly after the soft-deleted rows and in request order (see create)
        var createdBase = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        for (int i = 0; i < requested.size(); i++) {
            var a = requested.get(i);
            var address = new ShopCustomerAddress(customer.getId());
            address.initCreatedDateTime(createdBase.plus(i, java.time.temporal.ChronoUnit.MICROS));
            applyAddress(address, a);
            address.setDefaultShipping(anyShipping ? Boolean.TRUE.equals(a.defaultShipping()) : i == 0);
            address.setDefaultBilling(anyBilling ? Boolean.TRUE.equals(a.defaultBilling()) : i == 0);
            address.stamp(userId);
            addresses.save(address);
        }
    }

    private void clearDefaults(List<ShopCustomerAddress> list, boolean shipping, boolean billing) {
        var changed = false;
        for (var a : list) {
            if (shipping && a.isDefaultShipping()) { a.setDefaultShipping(false); changed = true; }
            if (billing && a.isDefaultBilling()) { a.setDefaultBilling(false); changed = true; }
        }
        if (changed) addresses.saveAllAndFlush(list);
    }

    private void touch(UUID userId, ShopCustomer customer) {
        customer.stamp(userId);
        customers.saveAndFlush(customer);
    }

    private List<CustomerDtos.DuplicateMatch> matches(Shop shop, String phoneKey, String emailKey, UUID excludeId, boolean pii) {
        if (phoneKey == null && emailKey == null) return List.of();
        var probe = new ShopCustomer(shop.getUuid());
        probe.setPhone(null, phoneKey);
        probe.setEmail(null, emailKey);
        var found = customers.findMatches(shop.getUuid(), phoneKey, emailKey).stream()
            .filter(c -> excludeId == null || !c.getId().equals(excludeId))
            .map(c -> new CustomerDtos.DuplicateMatch(c.getId(), c.getCustomerCode(), c.getDisplayName(), c.getPhone(), c.getEmail(), c.getStatus(), matchedOn(probe, c)))
            .toList();
        return maskMatches(found, pii);
    }

    private static List<CustomerDtos.DuplicateMatch> maskMatches(List<CustomerDtos.DuplicateMatch> list, boolean pii) {
        if (pii || list == null) return list;
        return list.stream().map(m -> new CustomerDtos.DuplicateMatch(m.id(), m.customerCode(), maskName(m.displayName()), maskPhone(m.phone()),
            maskEmail(m.email()), m.status(), m.matchedOn())).toList();
    }

    private static List<String> matchedOn(ShopCustomer a, ShopCustomer b) {
        var on = new ArrayList<String>();
        if (a.getPhoneNormalized() != null && a.getPhoneNormalized().equals(b.getPhoneNormalized())) on.add("PHONE");
        if (a.getEmailNormalized() != null && a.getEmailNormalized().equals(b.getEmailNormalized())) on.add("EMAIL");
        return on;
    }

    private static String addressKey(ShopCustomerAddress a) {
        return String.join("|", key(a.getRecipientName()), Objects.toString(ContactNormalizer.phone(a.getPhone()), ""), key(a.getAddressLine1()),
            key(a.getAddressLine2()), key(a.getSubdistrict()), key(a.getDistrict()), key(a.getProvince()), key(a.getPostalCode()), key(a.getCountryCode()));
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private CustomerDtos.CustomerResponse toResponse(ShopCustomer c, boolean pii, boolean withAddresses, Long orderCount, List<CustomerDtos.DuplicateMatch> possible) {
        List<CustomerDtos.AddressResponse> list = null;
        if (withAddresses && pii) {
            list = addresses.findByCustomerIdAndDeletedAtIsNullOrderByCreatedDateTimeAscIdAsc(c.getId()).stream()
                .map(a -> new CustomerDtos.AddressResponse(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(), a.getAddressLine1(),
                    a.getAddressLine2(), a.getSubdistrict(), a.getDistrict(), a.getProvince(), a.getPostalCode(), a.getCountryCode(),
                    a.isDefaultShipping(), a.isDefaultBilling())).toList();
        }
        return new CustomerDtos.CustomerResponse(c.getId(), c.getCustomerCode(),
            pii ? c.getDisplayName() : maskName(c.getDisplayName()), pii ? c.getPhone() : maskPhone(c.getPhone()), pii ? c.getEmail() : maskEmail(c.getEmail()),
            c.getSource(), c.getStatus(), pii ? c.getNotes() : null, c.getTags().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
            c.getUserId(), c.getMergedIntoId(), c.getMergedAt(), list, orderCount, c.getCreatedDateTime(), c.getModifiedDateTime(), !pii,
            possible == null || possible.isEmpty() ? null : possible);
    }

    public static String maskName(String name) {
        if (name == null || name.isBlank()) return name;
        var cps = name.trim().codePoints().toArray();
        return new String(cps, 0, 1) + "***";
    }

    static String maskPhone(String phone) {
        if (phone == null) return null;
        var digits = phone.replaceAll("\\D", "");
        return digits.length() <= 4 ? "****" : "******" + digits.substring(digits.length() - 4);
    }

    static String maskEmail(String email) {
        if (email == null) return null;
        var at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    private Shop requireShop(UUID userId, Long shopNo) {
        var shop = shops.findByShopId(shopNo).orElseThrow(CustomerService::shopNotFound);
        if (userId == null) throw shopNotFound();
        var grants = authorization.grants(userId);
        if (!grants.superAdmin() && !grants.shopRoles().containsKey(shop.getId())) throw shopNotFound();
        return shop;
    }

    private boolean canReadPii(UUID userId, Long shopNo) {
        return authorization.checkShop(userId, shopNo, PII_PERMISSION) == AuthorizationService.ShopDecision.GRANTED;
    }

    private ShopCustomer requireCustomer(Shop shop, UUID customerId) {
        if (customerId == null) throw customerNotFound();
        return customers.findByIdAndShopIdAndDeletedAtIsNull(customerId, shop.getUuid()).orElseThrow(CustomerService::customerNotFound);
    }

    private ShopCustomer requireWritable(Shop shop, UUID customerId) {
        var customer = requireCustomer(shop, customerId);
        if (customer.getStatus() == CustomerStatus.MERGED) throw merged(customer);
        return customer;
    }

    private void audit(UUID userId, Shop shop, String action, String permission, UUID customerId, Map<String, Object> data) {
        audits.save(new CustomerAuditLog(userId, shop.getUuid(), action, permission, customerId, data == null || data.isEmpty() ? null : data));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field, String code, List<FieldViolation> violations) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            violations.add(violation(field, code));
            return null;
        }
    }

    private static CustomerDtos.CustomerWriteRequest emptyRequest() {
        return new CustomerDtos.CustomerWriteRequest(null, null, null, null, null, null, null, null, null, null);
    }

    private static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().replaceAll("\\s+", " ");
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static FieldViolation violation(String field, String code) {
        return new FieldViolation(field, code, MESSAGES.getOrDefault(code, "ข้อมูลไม่ถูกต้อง"));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static AuthException merged(ShopCustomer customer) {
        return new AuthException(HttpStatus.CONFLICT, "CUSTOMER_MERGED", "ลูกค้ารายนี้ถูกรวมเข้ากับลูกค้ารายอื่นแล้ว (" + customer.getMergedIntoId() + ")");
    }

    private static AuthException shopNotFound() { return new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"); }
    private static AuthException customerNotFound() { return new AuthException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "ไม่พบลูกค้า"); }
    private static AuthException addressNotFound() { return new AuthException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "ไม่พบที่อยู่"); }
}
