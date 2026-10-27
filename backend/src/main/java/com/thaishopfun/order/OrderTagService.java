package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Merchant order tags + internal notes (AB2). Buyer paths never call this. */
@Service
public class OrderTagService {
    public static final int MAX_NAME = 40;
    public static final int MAX_TAGS_PER_SHOP = 100;
    public static final int MAX_TAGS_PER_ORDER = 10;
    public static final int MAX_NOTE = 2000;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final OrderTagRepository tags;
    private final OrderTagAssignmentRepository assignments;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;

    public OrderTagService(OrderTagRepository tags, OrderTagAssignmentRepository assignments, OrderRepository orders,
                           OrderStatusHistoryRepository history) {
        this.tags = tags;
        this.assignments = assignments;
        this.orders = orders;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.TagView> listTags(UUID shopUuid) {
        var list = tags.findByShopIdOrderByNameAsc(shopUuid);
        var counts = usageCounts(list.stream().map(OrderTag::getId).toList());
        return list.stream()
            .map(tag -> new OrderDtos.TagView(tag.getId(), tag.getName(), tag.getColor(), counts.getOrDefault(tag.getId(), 0L).intValue()))
            .toList();
    }

    @Transactional
    public OrderDtos.TagView createTag(UUID shopUuid, UUID actorId, OrderDtos.OrderTagRequest body) {
        var name = requireName(body == null ? null : body.name());
        var color = requireColor(body == null ? null : body.color());
        if (tags.countByShopId(shopUuid) >= MAX_TAGS_PER_SHOP) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "TAG_LIMIT_EXCEEDED",
                "ร้านมีแท็กได้สูงสุด " + MAX_TAGS_PER_SHOP + " รายการ");
        }
        if (tags.existsByShopIdAndNameIgnoreCase(shopUuid, name)) {
            throw new AuthException(HttpStatus.CONFLICT, "TAG_NAME_DUPLICATE", "มีแท็กชื่อนี้อยู่แล้ว");
        }
        var tag = new OrderTag(shopUuid, name, color);
        tag.stamp(actorId);
        tags.save(tag);
        return new OrderDtos.TagView(tag.getId(), tag.getName(), tag.getColor(), 0);
    }

    @Transactional
    public OrderDtos.TagView updateTag(UUID shopUuid, UUID actorId, UUID tagId, OrderDtos.OrderTagRequest body) {
        var tag = tags.findByIdAndShopId(tagId, shopUuid)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "ไม่พบแท็ก"));
        var name = requireName(body == null ? null : body.name());
        var color = requireColor(body == null ? null : body.color());
        if (tags.existsByShopIdAndNameIgnoreCaseExcluding(shopUuid, name, tagId)) {
            throw new AuthException(HttpStatus.CONFLICT, "TAG_NAME_DUPLICATE", "มีแท็กชื่อนี้อยู่แล้ว");
        }
        tag.setName(name);
        tag.setColor(color);
        tag.stamp(actorId);
        tags.save(tag);
        long usage = assignments.countByTagId(tagId);
        return new OrderDtos.TagView(tag.getId(), tag.getName(), tag.getColor(), (int) usage);
    }

    @Transactional
    public void deleteTag(UUID shopUuid, UUID tagId) {
        var tag = tags.findByIdAndShopId(tagId, shopUuid)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "ไม่พบแท็ก"));
        tags.delete(tag); // assignments cascade via FK
    }

    @Transactional
    public OrderDtos.OrderTagsResponse setOrderTags(OrderActor actor, UUID orderId, OrderDtos.OrderTagsRequest body) {
        var order = load(actor, orderId);
        MerchantVisibility.requireAccepted(order, actor);
        requireVersion(order, body == null ? null : body.expectedVersion());
        var tagIds = body == null || body.tagIds() == null ? List.<UUID>of() : body.tagIds().stream().filter(Objects::nonNull).distinct().toList();
        if (tagIds.size() > MAX_TAGS_PER_ORDER) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "TAG_LIMIT_EXCEEDED",
                "ออเดอร์มีแท็กได้สูงสุด " + MAX_TAGS_PER_ORDER + " รายการ");
        }
        var found = tags.findByShopIdAndIdIn(order.getShopId(), tagIds);
        if (found.size() != tagIds.size()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "ไม่พบแท็ก");
        }
        assignments.deleteByOrderId(order.getId());
        for (var tagId : tagIds) {
            assignments.save(new OrderTagAssignment(order.getId(), tagId, actor.userId()));
        }
        order.stamp(actor.userId());
        orders.saveAndFlush(order);
        writeHistory(order, actor, "TAGS_UPDATED", Map.of(
            "event", "TAGS_UPDATED",
            "tags", found.stream().sorted(Comparator.comparing(OrderTag::getName)).map(OrderTag::getName).toList()));
        var views = found.stream()
            .sorted(Comparator.comparing(OrderTag::getName))
            .map(t -> new OrderDtos.TagView(t.getId(), t.getName(), t.getColor()))
            .toList();
        return new OrderDtos.OrderTagsResponse(order.getId(), views, order.getRowVersion());
    }

    @Transactional
    public OrderDtos.InternalNoteResponse updateInternalNote(OrderActor actor, UUID orderId, OrderDtos.InternalNoteRequest body) {
        var order = load(actor, orderId);
        MerchantVisibility.requireAccepted(order, actor);
        requireVersion(order, body == null ? null : body.expectedVersion());
        String note = body == null || body.internalNote() == null ? null : body.internalNote().trim();
        if (note != null && note.isEmpty()) note = null;
        if (note != null && note.length() > MAX_NOTE) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTE_TOO_LONG",
                "โน้ตยาวเกิน " + MAX_NOTE + " ตัวอักษร");
        }
        order.setInternalNote(note);
        order.stamp(actor.userId());
        orders.saveAndFlush(order);
        writeHistory(order, actor, "NOTE_UPDATED", Map.of(
            "event", "NOTE_UPDATED",
            "length", note == null ? 0 : note.length()));
        return new OrderDtos.InternalNoteResponse(order.getId(), order.getInternalNote(), order.getRowVersion());
    }

    /** Batch-load tag views for merchant list/detail. Empty map when no assignments. */
    @Transactional(readOnly = true)
    public Map<UUID, List<OrderDtos.TagView>> tagsByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Map.of();
        var rows = assignments.findByOrderIdIn(orderIds);
        if (rows.isEmpty()) return Map.of();
        var tagIds = rows.stream().map(OrderTagAssignment::getTagId).collect(Collectors.toSet());
        var tagMap = tags.findAllById(tagIds).stream().collect(Collectors.toMap(OrderTag::getId, t -> t));
        var grouped = new HashMap<UUID, List<OrderDtos.TagView>>();
        for (var row : rows) {
            var tag = tagMap.get(row.getTagId());
            if (tag == null) continue;
            grouped.computeIfAbsent(row.getOrderId(), id -> new ArrayList<>())
                .add(new OrderDtos.TagView(tag.getId(), tag.getName(), tag.getColor()));
        }
        for (var list : grouped.values()) {
            list.sort(Comparator.comparing(OrderDtos.TagView::name, String.CASE_INSENSITIVE_ORDER));
        }
        return grouped;
    }

    /** Apply add/remove tag ids for one order (bulk tags path). Returns new version. */
    @Transactional
    public int applyTagDelta(OrderActor actor, UUID orderId, Integer expectedVersion, List<UUID> addTagIds, List<UUID> removeTagIds) {
        var order = load(actor, orderId);
        MerchantVisibility.requireAccepted(order, actor);
        requireVersion(order, expectedVersion);
        var current = new HashSet<>(assignments.findByOrderId(orderId).stream().map(OrderTagAssignment::getTagId).toList());
        if (removeTagIds != null) current.removeAll(removeTagIds.stream().filter(Objects::nonNull).toList());
        if (addTagIds != null) current.addAll(addTagIds.stream().filter(Objects::nonNull).toList());
        if (current.size() > MAX_TAGS_PER_ORDER) {
            throw new AuthException(HttpStatus.UNPROCESSABLE_ENTITY, "TAG_LIMIT_EXCEEDED",
                "ออเดอร์มีแท็กได้สูงสุด " + MAX_TAGS_PER_ORDER + " รายการ");
        }
        var found = tags.findByShopIdAndIdIn(order.getShopId(), current);
        if (found.size() != current.size()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "ไม่พบแท็ก");
        }
        assignments.deleteByOrderId(order.getId());
        for (var tagId : current) {
            assignments.save(new OrderTagAssignment(order.getId(), tagId, actor.userId()));
        }
        order.stamp(actor.userId());
        orders.saveAndFlush(order);
        writeHistory(order, actor, "TAGS_UPDATED", Map.of(
            "event", "TAGS_UPDATED",
            "tags", found.stream().sorted(Comparator.comparing(OrderTag::getName)).map(OrderTag::getName).toList()));
        return order.getRowVersion();
    }

    private Map<UUID, Long> usageCounts(List<UUID> tagIds) {
        if (tagIds.isEmpty()) return Map.of();
        var map = new HashMap<UUID, Long>();
        for (var row : assignments.countByTagIdIn(tagIds)) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }

    private void writeHistory(Order order, OrderActor actor, String event, Map<String, Object> metadata) {
        var status = order.getStatus();
        var entry = new OrderStatusHistory(order.getId(), status, status, actor.userId(), actor.type(), null);
        var meta = new LinkedHashMap<>(metadata);
        meta.putIfAbsent("event", event);
        entry.setMetadataJson(OrderJson.write(meta));
        entry.stamp(actor.userId());
        history.save(entry);
    }

    private Order load(OrderActor actor, UUID orderId) {
        return orders.findById(orderId).filter(actor::canSee)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์"));
    }

    static void requireVersion(Order order, Integer expectedVersion) {
        if (expectedVersion == null) {
            throw new ValidationException(List.of(new FieldViolation("expectedVersion", "REQUIRED", "ต้องส่ง expectedVersion")));
        }
        if (expectedVersion != order.getRowVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "ORDER_VERSION_CONFLICT",
                "ออเดอร์ถูกแก้ไขโดยผู้อื่นแล้ว กรุณาโหลดข้อมูลใหม่");
        }
    }

    static String requireName(String raw) {
        var name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new ValidationException(List.of(new FieldViolation("name", "INVALID",
                "ชื่อแท็กต้องยาว 1-" + MAX_NAME + " ตัวอักษร")));
        }
        return name;
    }

    static String requireColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var color = raw.trim();
        if (!COLOR.matcher(color).matches()) {
            throw new ValidationException(List.of(new FieldViolation("color", "INVALID", "สีต้องเป็นรูปแบบ #RRGGBB")));
        }
        return color;
    }
}
