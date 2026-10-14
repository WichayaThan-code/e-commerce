package com.thaishopfun.catalog;

import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationMessages;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Option groups / values of VARIABLE products and the variant (SKU) to value mapping.
 * ProductService calls {@link #validate} before anything is written and {@link #sync} in the same transaction
 * after the SKU rows are saved. Names are compared trimmed and case-insensitively.
 */
@Component
class ProductVariantService {
    static final int MAX_GROUPS = 3;
    static final int MAX_VALUES = 50;
    static final int MAX_NAME = 50;

    private final ProductOptionGroupRepository groups;
    private final ProductOptionValueRepository values;
    private final ProductSkuOptionValueRepository mappings;
    private final ProductImageRepository images;
    private final EntityManager entities;

    ProductVariantService(ProductOptionGroupRepository groups, ProductOptionValueRepository values,
                          ProductSkuOptionValueRepository mappings, ProductImageRepository images, EntityManager entities) {
        this.groups = groups;
        this.values = values;
        this.mappings = mappings;
        this.images = images;
        this.entities = entities;
    }

    /** Requested groups, or (when the request has none) groups derived from the SKUs' optionValues in first-seen order. */
    static List<CatalogDtos.OptionGroupWriteRequest> effectiveGroups(CatalogDtos.ProductWriteRequest request) {
        if (request.optionGroups() != null) return request.optionGroups();
        var derived = new LinkedHashMap<String, LinkedHashMap<String, String>>();
        var names = new LinkedHashMap<String, String>();
        for (var sku : request.skus() == null ? List.<CatalogDtos.SkuWriteRequest>of() : request.skus()) {
            if (sku == null || sku.optionValues() == null) continue;
            for (var entry : sku.optionValues().entrySet()) {
                var group = clean(entry.getKey());
                var value = clean(entry.getValue());
                if (group == null) continue;
                names.putIfAbsent(key(group), group);
                var groupValues = derived.computeIfAbsent(key(group), ignored -> new LinkedHashMap<>());
                if (value != null) groupValues.putIfAbsent(key(value), value);
            }
        }
        return derived.entrySet().stream()
            .map(entry -> new CatalogDtos.OptionGroupWriteRequest(null, names.get(entry.getKey()),
                entry.getValue().values().stream().map(value -> new CatalogDtos.OptionValueWriteRequest(null, value)).toList()))
            .toList();
    }

    void validate(CatalogDtos.ProductWriteRequest request, ProductType type, Product current, Consumer<FieldViolation> add) {
        var requested = effectiveGroups(request);
        var explicit = request.optionGroups() != null;
        var incoming = request.skus() == null ? List.<CatalogDtos.SkuWriteRequest>of() : request.skus();
        var productImages = current == null ? java.util.Set.<UUID>of()
            : images.findByProduct_IdOrderBySortOrderAsc(current.getId()).stream().map(ProductImage::getId).collect(Collectors.toSet());
        for (var index = 0; index < incoming.size(); index++) {
            var sku = incoming.get(index);
            if (sku != null && sku.imageId() != null && !productImages.contains(sku.imageId())) {
                add.accept(ValidationMessages.violation("skus." + index + ".imageId", "VARIANT_IMAGE_INVALID"));
            }
        }
        if (type == ProductType.SIMPLE) {
            if (explicit && !requested.isEmpty()) add.accept(ValidationMessages.violation("optionGroups", "OPTION_GROUPS_NOT_ALLOWED"));
            return;
        }
        if (type != ProductType.VARIABLE) return;
        // VARIABLE without any option data stays free-form (older products); an explicit empty list is an error
        if (requested.isEmpty()) {
            if (explicit) add.accept(ValidationMessages.violation("optionGroups", "OPTION_GROUP_REQUIRED"));
            return;
        }
        if (requested.size() > MAX_GROUPS) add.accept(ValidationMessages.violation("optionGroups", "OPTION_GROUP_LIMIT"));

        var existingGroups = current == null ? List.<ProductOptionGroup>of() : groups.findByProduct_IdOrderBySortOrderAsc(current.getId());
        var existingValues = current == null ? List.<ProductOptionValue>of() : values.findByGroup_Product_IdOrderBySortOrderAsc(current.getId());
        var groupIds = existingGroups.stream().map(ProductOptionGroup::getId).collect(Collectors.toSet());
        var valueById = existingValues.stream().collect(Collectors.toMap(ProductOptionValue::getId, value -> value));

        // group name (key) -> set of value keys
        var allowed = new LinkedHashMap<String, java.util.Set<String>>();
        var display = new HashMap<String, String>();
        var keptValueIds = new HashSet<UUID>();
        for (var g = 0; g < requested.size(); g++) {
            var group = requested.get(g);
            var prefix = "optionGroups." + g + ".";
            if (group == null) {
                add.accept(ValidationMessages.violation(prefix + "name", "OPTION_GROUP_NAME_REQUIRED"));
                continue;
            }
            if (group.id() != null && !groupIds.contains(group.id())) add.accept(ValidationMessages.violation(prefix + "id", "OPTION_GROUP_NOT_FOUND"));
            var name = clean(group.name());
            if (name == null) add.accept(ValidationMessages.violation(prefix + "name", "OPTION_GROUP_NAME_REQUIRED"));
            else if (name.length() > MAX_NAME) add.accept(ValidationMessages.violation(prefix + "name", "OPTION_GROUP_NAME_TOO_LONG"));
            else if (allowed.containsKey(key(name))) add.accept(ValidationMessages.violation(prefix + "name", "OPTION_GROUP_DUPLICATE"));
            var groupValues = group.values() == null ? List.<CatalogDtos.OptionValueWriteRequest>of() : group.values();
            if (groupValues.isEmpty()) add.accept(ValidationMessages.violation(prefix + "values", "OPTION_VALUE_REQUIRED"));
            if (groupValues.size() > MAX_VALUES) add.accept(ValidationMessages.violation(prefix + "values", "OPTION_VALUE_LIMIT"));
            var seen = new HashSet<String>();
            for (var v = 0; v < groupValues.size(); v++) {
                var item = groupValues.get(v);
                var valuePrefix = prefix + "values." + v + ".";
                var value = item == null ? null : clean(item.value());
                if (item != null && item.id() != null) {
                    var known = valueById.get(item.id());
                    if (known == null || (group.id() != null && !known.getGroup().getId().equals(group.id()))) {
                        add.accept(ValidationMessages.violation(valuePrefix + "id", "OPTION_VALUE_NOT_FOUND"));
                    } else {
                        keptValueIds.add(item.id());
                    }
                }
                if (value == null) add.accept(ValidationMessages.violation(valuePrefix + "value", "OPTION_VALUE_REQUIRED"));
                else if (value.length() > MAX_NAME) add.accept(ValidationMessages.violation(valuePrefix + "value", "OPTION_VALUE_TOO_LONG"));
                else if (!seen.add(key(value))) add.accept(ValidationMessages.violation(valuePrefix + "value", "OPTION_VALUE_DUPLICATE"));
            }
            if (name != null && !allowed.containsKey(key(name))) {
                allowed.put(key(name), seen);
                display.put(key(name), name);
            }
        }

        // values that disappear from a group that is kept: a request SKU still pointing at one is "in use"
        var removed = new HashMap<String, java.util.Set<String>>();
        for (var value : existingValues) {
            if (keptValueIds.contains(value.getId())) continue;
            var groupKey = key(value.getGroup().getName());
            var still = allowed.get(groupKey);
            if (still != null && still.contains(key(value.getValue()))) continue;
            removed.computeIfAbsent(groupKey, ignored -> new HashSet<>()).add(key(value.getValue()));
        }

        var combinations = new HashMap<String, Integer>();
        for (var index = 0; index < incoming.size(); index++) {
            var sku = incoming.get(index);
            if (sku == null) continue;
            var prefix = "skus." + index + ".optionValues";
            var chosen = new HashMap<String, String>();
            if (sku.optionValues() != null) {
                for (var entry : sku.optionValues().entrySet()) {
                    var group = clean(entry.getKey());
                    if (group == null) continue;
                    if (!allowed.containsKey(key(group))) {
                        add.accept(ValidationMessages.violation(prefix + "." + group, "OPTION_GROUP_UNKNOWN"));
                        continue;
                    }
                    var value = clean(entry.getValue());
                    if (value == null) continue;
                    if (!allowed.get(key(group)).contains(key(value))) {
                        var inUse = removed.getOrDefault(key(group), java.util.Set.of()).contains(key(value));
                        add.accept(ValidationMessages.violation(prefix + "." + group, inUse ? "OPTION_VALUE_IN_USE" : "OPTION_VALUE_UNKNOWN"));
                        continue;
                    }
                    chosen.put(key(group), key(value));
                }
            }
            var complete = true;
            for (var group : allowed.keySet()) {
                if (!chosen.containsKey(group)) {
                    complete = false;
                    add.accept(ValidationMessages.violation(prefix + "." + display.get(group), "VARIANT_OPTION_REQUIRED"));
                }
            }
            if (!complete) continue;
            var combination = allowed.keySet().stream().map(group -> group + "=" + chosen.get(group)).collect(Collectors.joining("|"));
            if (combinations.putIfAbsent(combination, index) != null) add.accept(ValidationMessages.violation(prefix, "VARIANT_COMBINATION_DUPLICATE"));
        }
    }

    /**
     * Writes groups, values and mappings. {@code ordered} holds the saved SKU entity for each request SKU (same order).
     * Groups/values no longer requested are deleted (their mapping rows go with them); soft-deleted SKUs keep their
     * option_values / option_label snapshot as history.
     */
    void sync(Product product, CatalogDtos.ProductWriteRequest request, List<ProductSku> ordered, UUID userId) {
        var incoming = request.skus() == null ? List.<CatalogDtos.SkuWriteRequest>of() : request.skus();
        var productImages = images.findByProduct_IdOrderBySortOrderAsc(product.getId()).stream()
            .collect(Collectors.toMap(ProductImage::getId, image -> image));
        for (var index = 0; index < ordered.size(); index++) {
            var imageId = incoming.get(index).imageId();
            ordered.get(index).setImage(imageId == null ? null : productImages.get(imageId));
        }
        var existingGroups = groups.findByProduct_IdOrderBySortOrderAsc(product.getId());
        var existingValues = values.findByGroup_Product_IdOrderBySortOrderAsc(product.getId());
        var requested = product.getProductType() == ProductType.VARIABLE ? effectiveGroups(request) : List.<CatalogDtos.OptionGroupWriteRequest>of();

        var keptGroups = new LinkedHashMap<String, ProductOptionGroup>();
        var keptValues = new HashMap<String, ProductOptionValue>();
        var matchedGroups = new HashMap<Integer, ProductOptionGroup>();
        var matchedValues = new HashMap<String, ProductOptionValue>();
        var removedValues = new ArrayList<ProductOptionValue>();
        // 1. match requested rows to existing ones and park them on a temporary name, so renames and swaps
        //    never collide with the unique constraints (flushed before any insert)
        for (var g = 0; g < requested.size(); g++) {
            var spec = requested.get(g);
            var name = clean(spec.name());
            var group = existingGroups.stream().filter(item -> spec.id() != null ? item.getId().equals(spec.id()) : key(item.getName()).equals(key(name)))
                .filter(item -> !matchedGroups.containsValue(item)).findFirst().orElse(null);
            if (group == null) continue;
            matchedGroups.put(g, group);
            group.setName("~" + group.getId());
            group.stamp(userId);
            var ownValues = existingValues.stream().filter(value -> value.getGroup().getId().equals(group.getId())).toList();
            var specValues = spec.values() == null ? List.<CatalogDtos.OptionValueWriteRequest>of() : spec.values();
            for (var v = 0; v < specValues.size(); v++) {
                var item = specValues.get(v);
                var text = clean(item.value());
                var value = ownValues.stream().filter(candidate -> item.id() != null ? candidate.getId().equals(item.id()) : key(candidate.getValue()).equals(key(text)))
                    .filter(candidate -> !matchedValues.containsValue(candidate)).findFirst().orElse(null);
                if (value == null) continue;
                matchedValues.put(g + "|" + v, value);
                value.setValue("~" + value.getId());
                value.stamp(userId);
            }
            ownValues.stream().filter(value -> !matchedValues.containsValue(value)).forEach(removedValues::add);
        }
        var removedGroups = existingGroups.stream().filter(group -> !matchedGroups.containsValue(group)).toList();
        removedGroups.forEach(group -> existingValues.stream().filter(value -> value.getGroup().getId().equals(group.getId())).forEach(removedValues::add));
        entities.flush();
        // 2. drop what is no longer requested (mapping rows first)
        deleteMappings(mappings.findByValue_IdIn(ids(removedValues, ProductOptionValue::getId)));
        deleteMappings(mappings.findByGroup_IdIn(ids(removedGroups, ProductOptionGroup::getId)));
        entities.flush();
        removedValues.forEach(entities::remove);
        entities.flush();
        removedGroups.forEach(entities::remove);
        entities.flush();
        // 3. final names / order, then the new rows
        for (var g = 0; g < requested.size(); g++) {
            var spec = requested.get(g);
            var name = clean(spec.name());
            var group = matchedGroups.get(g);
            if (group == null) {
                group = new ProductOptionGroup(product, name, g);
                group.stamp(userId);
                entities.persist(group);
            } else {
                group.setName(name);
                group.setSortOrder(g);
            }
            keptGroups.put(key(name), group);
            var specValues = spec.values() == null ? List.<CatalogDtos.OptionValueWriteRequest>of() : spec.values();
            for (var v = 0; v < specValues.size(); v++) {
                var text = clean(specValues.get(v).value());
                var value = matchedValues.get(g + "|" + v);
                if (value == null) {
                    value = new ProductOptionValue(group, text, v);
                    value.stamp(userId);
                    entities.persist(value);
                } else {
                    value.setValue(text);
                    value.setSortOrder(v);
                }
                keptValues.put(key(name) + "|" + key(text), value);
            }
        }

        // mapping rows of the saved SKUs are rewritten from the request
        deleteMappings(mappings.findBySku_IdIn(ids(ordered, ProductSku::getId)));
        entities.flush();
        for (var index = 0; index < ordered.size(); index++) {
            var sku = ordered.get(index);
            if (keptGroups.isEmpty()) continue;
            var chosen = new HashMap<String, String>();
            incoming.get(index).optionValues().forEach((group, value) -> {
                if (clean(group) != null && clean(value) != null) chosen.put(key(clean(group)), clean(value));
            });
            var snapshot = new LinkedHashMap<String, String>();
            for (var entry : keptGroups.entrySet()) {
                var value = keptValues.get(entry.getKey() + "|" + key(chosen.get(entry.getKey())));
                var mapping = new ProductSkuOptionValue(sku, value);
                mapping.stamp(userId);
                entities.persist(mapping);
                snapshot.put(clean(entry.getValue().getName()), clean(chosen.get(entry.getKey())));
            }
            sku.setOptionValues(JsonMaps.write(snapshot));
            if (blank(incoming.get(index).optionLabel())) sku.setOptionLabel(String.join(" / ", snapshot.values()));
        }
    }

    /** Option groups with their values, in display order. */
    List<CatalogDtos.OptionGroupResponse> groupViews(UUID productId) {
        var allValues = values.findByGroup_Product_IdOrderBySortOrderAsc(productId);
        return groups.findByProduct_IdOrderBySortOrderAsc(productId).stream()
            .map(group -> new CatalogDtos.OptionGroupResponse(group.getId(), group.getName(), group.getSortOrder(),
                allValues.stream().filter(value -> value.getGroup().getId().equals(group.getId()))
                    .map(value -> new CatalogDtos.OptionValueResponse(value.getId(), value.getValue(), value.getSortOrder())).toList()))
            .toList();
    }

    /** Option values of each SKU, ordered by group. */
    Map<UUID, List<CatalogDtos.SkuOptionResponse>> skuOptions(Collection<UUID> skuIds) {
        if (skuIds.isEmpty()) return Map.of();
        return mappings.findBySku_IdIn(skuIds).stream()
            .sorted(java.util.Comparator.comparingInt((ProductSkuOptionValue mapping) -> mapping.getGroup().getSortOrder()))
            .collect(Collectors.groupingBy(mapping -> mapping.getSku().getId(), LinkedHashMap::new, Collectors.mapping(
                mapping -> new CatalogDtos.SkuOptionResponse(mapping.getGroup().getId(), mapping.getGroup().getName(), mapping.getValue().getId(), mapping.getValue().getValue()),
                Collectors.toList())));
    }

    /** Every combination of the given groups (helper for the form; nothing is saved). */
    static List<CatalogDtos.VariantCombination> combinations(CatalogDtos.VariantCombinationRequest request) {
        var specs = request == null || request.optionGroups() == null ? List.<CatalogDtos.OptionGroupWriteRequest>of() : request.optionGroups();
        var problems = new LinkedHashMap<String, FieldViolation>();
        if (specs.isEmpty()) problems.put("optionGroups", ValidationMessages.violation("optionGroups", "OPTION_GROUP_REQUIRED"));
        if (specs.size() > MAX_GROUPS) problems.put("optionGroups", ValidationMessages.violation("optionGroups", "OPTION_GROUP_LIMIT"));
        var names = new HashSet<String>();
        var lists = new ArrayList<List<String>>();
        for (var g = 0; g < specs.size(); g++) {
            var spec = specs.get(g);
            var name = spec == null ? null : clean(spec.name());
            if (name == null) problems.put("optionGroups." + g + ".name", ValidationMessages.violation("optionGroups." + g + ".name", "OPTION_GROUP_NAME_REQUIRED"));
            else if (!names.add(key(name))) problems.put("optionGroups." + g + ".name", ValidationMessages.violation("optionGroups." + g + ".name", "OPTION_GROUP_DUPLICATE"));
            var seen = new LinkedHashMap<String, String>();
            if (spec != null && spec.values() != null) {
                for (var v = 0; v < spec.values().size(); v++) {
                    var value = spec.values().get(v) == null ? null : clean(spec.values().get(v).value());
                    var field = "optionGroups." + g + ".values." + v + ".value";
                    if (value == null) problems.put(field, ValidationMessages.violation(field, "OPTION_VALUE_REQUIRED"));
                    else if (seen.putIfAbsent(key(value), value) != null) problems.put(field, ValidationMessages.violation(field, "OPTION_VALUE_DUPLICATE"));
                }
            }
            if (seen.isEmpty()) problems.putIfAbsent("optionGroups." + g + ".values", ValidationMessages.violation("optionGroups." + g + ".values", "OPTION_VALUE_REQUIRED"));
            lists.add(List.copyOf(seen.values()));
        }
        var total = lists.stream().mapToLong(List::size).reduce(1, (a, b) -> a * b);
        if (problems.isEmpty() && total > 100) problems.put("optionGroups", ValidationMessages.violation("optionGroups", "SKU_LIMIT"));
        if (!problems.isEmpty()) throw new com.thaishopfun.common.ValidationException(List.copyOf(problems.values()));
        var result = new ArrayList<CatalogDtos.VariantCombination>();
        var prefix = clean(request.skuPrefix());
        combine(specs, lists, 0, new LinkedHashMap<>(), prefix, result);
        return result;
    }

    private static void combine(List<CatalogDtos.OptionGroupWriteRequest> specs, List<List<String>> lists, int depth,
                                LinkedHashMap<String, String> current, String prefix, List<CatalogDtos.VariantCombination> out) {
        if (depth == lists.size()) {
            var parts = new ArrayList<String>();
            if (prefix != null) parts.add(prefix);
            current.values().forEach(value -> parts.add(value));
            var code = parts.stream().map(part -> part.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-+|-+$", ""))
                .filter(part -> !part.isEmpty()).collect(Collectors.joining("-"));
            if (code.isEmpty()) code = "SKU-" + (out.size() + 1);
            out.add(new CatalogDtos.VariantCombination(String.join(" / ", current.values()), new LinkedHashMap<>(current), code.length() > 64 ? code.substring(0, 64) : code));
            return;
        }
        for (var value : lists.get(depth)) {
            current.put(clean(specs.get(depth).name()), value);
            combine(specs, lists, depth + 1, current, prefix, out);
            current.remove(clean(specs.get(depth).name()));
        }
    }

    private void deleteMappings(List<ProductSkuOptionValue> rows) {
        rows.forEach(entities::remove);
    }

    private static <T> List<UUID> ids(Collection<T> rows, java.util.function.Function<T, UUID> id) {
        return rows.stream().map(id).filter(Objects::nonNull).toList();
    }

    static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
