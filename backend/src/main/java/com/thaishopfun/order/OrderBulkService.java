package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Bulk accept / process / ship / cancel / tags with per-item transactions + idempotency (AB2). */
@Service
public class OrderBulkService {
    public static final int MAX_ITEMS = 50;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OrderBulkService.class);
    static final String VERSION_CONFLICT_MESSAGE = "ออเดอร์ถูกแก้ไขโดยผู้อื่น กรุณาโหลดใหม่แล้วลองอีกครั้ง";
    static final String GENERIC_FAILURE_MESSAGE = "ดำเนินการไม่สำเร็จ กรุณาลองใหม่อีกครั้ง";

    private final OrderBulkRequestRepository bulkRequests;
    private final OrderTransitionService transitions;
    private final OrderTagService tagService;
    private final TransactionTemplate requiresNew;

    public OrderBulkService(OrderBulkRequestRepository bulkRequests, OrderTransitionService transitions,
                            OrderTagService tagService, PlatformTransactionManager txManager) {
        this.bulkRequests = bulkRequests;
        this.transitions = transitions;
        this.tagService = tagService;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public OrderDtos.BulkResponse runAction(UUID shopUuid, UUID actorUserId, OrderActor actor, String action,
                                            OrderDtos.BulkRequest body, String idempotencyKey) {
        OrderCheckoutService.requireKey(idempotencyKey);
        var key = idempotencyKey.trim();
        var normalizedAction = normalizeAction(action);
        validateActionBody(normalizedAction, body);
        var hash = requestHash(normalizedAction, body);
        var replay = replayOrConflict(shopUuid, actorUserId, key, hash);
        if (replay != null) return replay;

        var results = new ArrayList<OrderDtos.BulkResultItem>();
        for (var item : body.items()) {
            results.add(runOneAction(actor, normalizedAction, item, body));
        }
        return store(shopUuid, actorUserId, key, normalizedAction, hash, results);
    }

    @Transactional
    public OrderDtos.BulkResponse runTags(UUID shopUuid, UUID actorUserId, OrderActor actor,
                                          OrderDtos.BulkTagsRequest body, String idempotencyKey) {
        OrderCheckoutService.requireKey(idempotencyKey);
        var key = idempotencyKey.trim();
        validateTagsBody(body);
        var hash = requestHash("tags", body);
        var replay = replayOrConflict(shopUuid, actorUserId, key, hash);
        if (replay != null) return replay;

        var results = new ArrayList<OrderDtos.BulkResultItem>();
        for (var item : body.items()) {
            results.add(runOneTags(actor, item, body));
        }
        return store(shopUuid, actorUserId, key, "tags", hash, results);
    }

    private OrderDtos.BulkResultItem runOneAction(OrderActor actor, String action, OrderDtos.BulkItem item,
                                                  OrderDtos.BulkRequest body) {
        try {
            return requiresNew.execute(status -> {
                var req = transitionRequest(action, item, body);
                var result = transitions.transition(item.orderId(), actor, toAction(action), req);
                return new OrderDtos.BulkResultItem(item.orderId(), true, null, null, result.order().getRowVersion());
            });
        } catch (RuntimeException ex) {
            return failure(item.orderId(), action, ex);
        }
    }

    private OrderDtos.BulkResultItem runOneTags(OrderActor actor, OrderDtos.BulkItem item, OrderDtos.BulkTagsRequest body) {
        try {
            return requiresNew.execute(status -> {
                int version = tagService.applyTagDelta(actor, item.orderId(), item.expectedVersion(),
                    body.addTagIds(), body.removeTagIds());
                return new OrderDtos.BulkResultItem(item.orderId(), true, null, null, version);
            });
        } catch (RuntimeException ex) {
            return failure(item.orderId(), "tags", ex);
        }
    }

    private OrderDtos.BulkResponse store(UUID shopUuid, UUID actorUserId, String key, String action, String hash,
                                         List<OrderDtos.BulkResultItem> results) {
        int ok = (int) results.stream().filter(OrderDtos.BulkResultItem::ok).count();
        var response = new OrderDtos.BulkResponse(action, results.size(), ok, results.size() - ok, results, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap = JSON.convertValue(response, Map.class);
        try {
            // own transaction + flush so a concurrent duplicate (same shop/actor/key) surfaces here, not as a 500 at commit
            requiresNew.executeWithoutResult(status ->
                bulkRequests.saveAndFlush(new OrderBulkRequest(shopUuid, actorUserId, key, action, hash, asMap)));
        } catch (DataIntegrityViolationException ex) {
            var replay = replayOrConflict(shopUuid, actorUserId, key, hash);
            if (replay != null) return replay;
            throw ex;
        }
        return response;
    }

    /**
     * Per-item failure without leaking internals: AuthException / ValidationException keep their curated code + message,
     * lock / optimistic conflicts become ORDER_VERSION_CONFLICT, anything else REQUEST_FAILED (logged server-side only).
     */
    private static OrderDtos.BulkResultItem failure(UUID orderId, String action, RuntimeException ex) {
        var cause = unwrap(ex);
        if (cause instanceof AuthException auth) {
            return new OrderDtos.BulkResultItem(orderId, false, auth.getCode(), auth.getMessage(), null);
        }
        if (isConcurrencyFailure(ex)) {
            return new OrderDtos.BulkResultItem(orderId, false, "ORDER_VERSION_CONFLICT", VERSION_CONFLICT_MESSAGE, null);
        }
        log.error("Bulk {} failed for order {}", action, orderId, ex);
        return new OrderDtos.BulkResultItem(orderId, false, "REQUEST_FAILED", GENERIC_FAILURE_MESSAGE, null);
    }

    private static boolean isConcurrencyFailure(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause() == current ? null : current.getCause()) {
            if (current instanceof ConcurrencyFailureException
                || current instanceof jakarta.persistence.OptimisticLockException
                || current instanceof jakarta.persistence.PessimisticLockException
                || current instanceof jakarta.persistence.LockTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private OrderDtos.BulkResponse replayOrConflict(UUID shopUuid, UUID actorUserId, String key, String hash) {
        var existing = bulkRequests.findByShopIdAndActorUserIdAndIdempotencyKey(shopUuid, actorUserId, key);
        if (existing.isEmpty()) return null;
        var row = existing.get();
        if (!row.getRequestHash().equals(hash)) {
            throw new AuthException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key นี้ถูกใช้กับคำขออื่นแล้ว");
        }
        var prior = JSON.convertValue(row.getResponse(), OrderDtos.BulkResponse.class);
        return new OrderDtos.BulkResponse(prior.action(), prior.total(), prior.succeeded(), prior.failed(), prior.results(), true);
    }

    private static OrderDtos.TransitionRequest transitionRequest(String action, OrderDtos.BulkItem item, OrderDtos.BulkRequest body) {
        String tracking = null;
        if ("ship".equals(action) && body.trackingNumbers() != null) {
            tracking = body.trackingNumbers().get(item.orderId().toString());
            if (tracking == null) tracking = body.trackingNumbers().get(item.orderId().toString().toLowerCase(Locale.ROOT));
        }
        return new OrderDtos.TransitionRequest(
            body.reason(),
            "ship".equals(action) ? body.carrierCode() : null,
            tracking,
            "process".equals(action) ? body.status() : null,
            null, null, null, null,
            item.expectedVersion(),
            null);
    }

    private static OrderTransitionService.Action toAction(String action) {
        return switch (action) {
            case "accept" -> OrderTransitionService.Action.ACCEPT;
            case "process" -> OrderTransitionService.Action.PROCESS;
            case "ship" -> OrderTransitionService.Action.SHIP;
            case "cancel" -> OrderTransitionService.Action.CANCEL;
            default -> throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID", "action ไม่รองรับ");
        };
    }

    private static String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            throw new ValidationException(List.of(new FieldViolation("action", "INVALID", "action ไม่ถูกต้อง")));
        }
        var value = action.trim().toLowerCase(Locale.ROOT);
        if (!List.of("accept", "process", "ship", "cancel").contains(value)) {
            throw new ValidationException(List.of(new FieldViolation("action", "INVALID", "action ต้องเป็น accept|process|ship|cancel")));
        }
        return value;
    }

    private static void validateActionBody(String action, OrderDtos.BulkRequest body) {
        var items = requireItems(body == null ? null : body.items());
        if ("cancel".equals(action) && (body.reason() == null || body.reason().isBlank())) {
            throw new ValidationException(List.of(new FieldViolation("reason", "REQUIRED", "ต้องระบุเหตุผลเมื่อยกเลิก")));
        }
        if ("process".equals(action) && (body.status() == null || body.status().isBlank())) {
            throw new ValidationException(List.of(new FieldViolation("status", "REQUIRED", "ต้องระบุ status เมื่อเตรียมสินค้า")));
        }
        if ("ship".equals(action)) {
            if (body.carrierCode() == null || body.carrierCode().isBlank()) {
                throw new ValidationException(List.of(new FieldViolation("carrierCode", "REQUIRED", "ต้องระบุ carrierCode")));
            }
            var tracking = body.trackingNumbers() == null ? Map.<String, String>of() : body.trackingNumbers();
            var missing = new ArrayList<FieldViolation>();
            for (var item : items) {
                var key = item.orderId().toString();
                var value = tracking.get(key);
                if (value == null || value.isBlank()) {
                    missing.add(new FieldViolation("trackingNumbers." + key, "REQUIRED", "ต้องระบุเลขพัสดุของทุกออเดอร์"));
                }
            }
            if (!missing.isEmpty()) throw new ValidationException(missing);
        }
    }

    private static void validateTagsBody(OrderDtos.BulkTagsRequest body) {
        requireItems(body == null ? null : body.items());
        boolean hasAdd = body.addTagIds() != null && !body.addTagIds().isEmpty();
        boolean hasRemove = body.removeTagIds() != null && !body.removeTagIds().isEmpty();
        if (!hasAdd && !hasRemove) {
            throw new ValidationException(List.of(new FieldViolation("addTagIds", "REQUIRED", "ต้องระบุ addTagIds หรือ removeTagIds")));
        }
    }

    private static List<OrderDtos.BulkItem> requireItems(List<OrderDtos.BulkItem> items) {
        if (items == null || items.isEmpty()) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "BULK_EMPTY", "ต้องเลือกอย่างน้อย 1 ออเดอร์");
        }
        if (items.size() > MAX_ITEMS) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "BULK_TOO_MANY",
                "เลือกได้สูงสุด " + MAX_ITEMS + " รายการ");
        }
        var seen = new LinkedHashSet<UUID>();
        for (var item : items) {
            if (item == null || item.orderId() == null) {
                throw new ValidationException(List.of(new FieldViolation("items.orderId", "REQUIRED", "ต้องระบุ orderId")));
            }
            if (item.expectedVersion() == null) {
                throw new ValidationException(List.of(new FieldViolation("items.expectedVersion", "REQUIRED", "ต้องระบุ expectedVersion")));
            }
            if (!seen.add(item.orderId())) {
                throw new ValidationException(List.of(new FieldViolation("items", "INVALID", "orderId ซ้ำในรายการ")));
            }
        }
        return items;
    }

    private static String requestHash(String action, Object body) {
        try {
            var payload = JSON.writeValueAsString(Map.of("action", action, "body", body == null ? Map.of() : body));
            var digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot hash bulk request", ex);
        }
    }

    private static Throwable unwrap(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            if (current instanceof AuthException) return current;
            current = current.getCause();
        }
        return current;
    }
}
