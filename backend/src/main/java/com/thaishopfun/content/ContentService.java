package com.thaishopfun.content;

import com.thaishopfun.audit.PlatformAuditService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductStatus;
import com.thaishopfun.content.ContentDtos.*;
import com.thaishopfun.merchant.MerchantStorageService;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.ShopStatus;
import com.thaishopfun.order.MediaFile;
import com.thaishopfun.order.MediaFileRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Articles CMS: localized public reads (published only) and permission-guarded admin CRUD for articles and
 * categories (audited). Thai ({@code title / excerpt / body_md}) is the fallback for any blank translation.
 */
@Service
public class ContentService {
    static final String MANAGE = "PLATFORM_CONTENT_MANAGE";
    static final String COVER_PURPOSE = "BLOG";
    static final String COVER_FOLDER = "article-covers";
    static final String COVER_URL_PREFIX = "/api/article-covers/";
    private static final Pattern SLUG = Pattern.compile("[a-z0-9-]+");
    /** Collides with GET /api/articles/featured. */
    private static final String RESERVED_SLUG = "featured";
    private static final int MAX_ARTICLE_SLUG = 150, MAX_CATEGORY_SLUG = 100, MAX_TITLE = 255, MAX_EXCERPT = 500,
        MAX_BODY = 200_000, MAX_NAME = 150, MAX_DESCRIPTION = 500, MAX_META_TITLE = 120, MAX_META_DESCRIPTION = 200,
        MAX_TAGS = 10, MAX_TAG = 50, MAX_RELATED_PRODUCTS = 10, MAX_READING_MINUTES = 240;
    private static final int DEFAULT_PAGE_SIZE = 12, MAX_PAGE_SIZE = 50, FEATURED_LIMIT = 6, RELATED_LIMIT = 3, MAX_QUERY = 100;

    private final BlogPostRepository posts;
    private final BlogCategoryRepository categories;
    private final MediaFileRepository media;
    private final MerchantStorageService storage;
    private final UserAccountRepository users;
    private final ShopRepository shops;
    private final ProductRepository products;
    private final PlatformAuditService audit;

    public ContentService(BlogPostRepository posts, BlogCategoryRepository categories, MediaFileRepository media,
                          MerchantStorageService storage, UserAccountRepository users, ShopRepository shops,
                          ProductRepository products, PlatformAuditService audit) {
        this.posts = posts;
        this.categories = categories;
        this.media = media;
        this.storage = storage;
        this.users = users;
        this.shops = shops;
        this.products = products;
        this.audit = audit;
    }

    // ===================== Public =====================

    @Transactional(readOnly = true)
    public PageResponse<ArticleCard> listArticles(String lang, String category, String q, String tag, String audience,
                                                  Integer page, Integer size) {
        String l = normalizeLang(lang);
        // Native searchPublic requires "" (never null) for every string filter — Postgres cast(:x as text) rejects nulls.
        String categoryFilter = blankToEmpty(category);
        String audienceFilter = audience == null || audience.isBlank() ? "" : parseAudience(audience);
        String tagFilter = blankToEmpty(tag);
        String queryFilter = limitQuery(q);
        // Unsorted Pageable: ORDER BY is fixed in the native SQL (published_at desc, id asc).
        var pageable = PageRequest.of(Math.max(0, page == null ? 0 : page),
            Math.min(MAX_PAGE_SIZE, Math.max(1, size == null ? DEFAULT_PAGE_SIZE : size)));
        Page<BlogPost> result = posts.searchPublic(Instant.now(), categoryFilter, audienceFilter, tagFilter,
            queryFilter, pageable);
        return new PageResponse<>(result.getContent().stream().map(p -> toCard(p, l)).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<ArticleCard> featured(String lang) {
        String l = normalizeLang(lang);
        return posts.findFeatured(Instant.now(), PageRequest.of(0, FEATURED_LIMIT)).stream().map(p -> toCard(p, l)).toList();
    }

    @Transactional(readOnly = true)
    public ArticleDetail articleDetail(String slug, String lang) {
        String l = normalizeLang(lang);
        var now = Instant.now();
        var post = posts.findPublicBySlug(blankToEmpty(slug), now).orElseThrow(ContentService::notFound);

        var related = new ArrayList<BlogPost>();
        if (post.getCategory() != null) {
            related.addAll(posts.findRelatedInCategory(now, post.getId(), post.getCategory().getId(), PageRequest.of(0, RELATED_LIMIT)));
        }
        if (related.size() < RELATED_LIMIT) {
            var have = new HashSet<UUID>();
            related.forEach(p -> have.add(p.getId()));
            for (var recent : posts.findRecent(now, post.getId(), PageRequest.of(0, RELATED_LIMIT * 2))) {
                if (related.size() >= RELATED_LIMIT) break;
                if (have.add(recent.getId())) related.add(recent);
            }
        }

        return new ArticleDetail(post.getId(), post.getSlug(),
            pick(l, post.getTitle(), post.getTitleEn(), post.getTitleZh()),
            pick(l, post.getExcerpt(), post.getExcerptEn(), post.getExcerptZh()),
            pick(l, post.getBodyMd(), post.getBodyMdEn(), post.getBodyMdZh()),
            categoryRef(post.getCategory(), l), coverUrl(post), post.getReadingMinutes(), List.copyOf(post.getTags()),
            post.getAudience(), post.isFeatured(), post.getPublishedAt(), authorName(post.getAuthorId()),
            blankToDefault(post.getMetaTitle(), pick(l, post.getTitle(), post.getTitleEn(), post.getTitleZh())),
            blankToDefault(post.getMetaDescription(), pick(l, post.getExcerpt(), post.getExcerptEn(), post.getExcerptZh())),
            related.stream().map(p -> toCard(p, l)).toList(),
            relatedShop(post, l), relatedProducts(post, l));
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories(String lang) {
        String l = normalizeLang(lang);
        var now = Instant.now();
        return categories.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(c -> new CategoryView(c.getId(), c.getSlug(),
                pick(l, c.getNameTh(), c.getNameEn(), c.getNameZh()),
                pick(l, c.getDescriptionTh(), c.getDescriptionEn(), c.getDescriptionZh()),
                posts.countPublishedInCategory(c.getId(), now)))
            .toList();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> serveCover(UUID mediaId) {
        var file = media.findById(mediaId)
            .filter(m -> COVER_PURPOSE.equals(m.getPurpose()) && "PUBLIC".equals(m.getVisibility()))
            .orElseThrow(ContentService::coverNotFound);
        var path = storage.openPublicImage(file.getStorageKey()).orElseThrow(ContentService::coverNotFound);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(new FileSystemResource(path));
    }

    // ===================== Admin: articles =====================

    @Transactional(readOnly = true)
    public PageResponse<AdminArticleSummary> adminArticles(String status, String category, String q, Integer page, Integer size) {
        String st = status == null || status.isBlank() ? "" : parseStatus(status);
        var pageable = PageRequest.of(Math.max(0, page == null ? 0 : page),
            Math.min(MAX_PAGE_SIZE, Math.max(1, size == null ? 20 : size)));
        var result = posts.searchAdmin(st, blankToEmpty(category), limitQuery(q), pageable);
        return new PageResponse<>(result.getContent().stream().map(this::toSummary).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminArticle adminArticle(UUID id) {
        return toAdmin(post(id));
    }

    @Transactional
    public AdminArticle createArticle(ArticleRequest r, UUID actorId) {
        var post = new BlogPost(actorId);
        applyArticle(post, r, true);
        post.setStatus(BlogPost.DRAFT);
        posts.save(post);
        recordAudit(actorId, "ARTICLE_CREATE", "ARTICLE", post.getId());
        return toAdmin(post);
    }

    @Transactional
    public AdminArticle updateArticle(UUID id, ArticleRequest r, UUID actorId) {
        var post = post(id);
        applyArticle(post, r, false);
        post.stamp(actorId);
        posts.save(post);
        recordAudit(actorId, "ARTICLE_UPDATE", "ARTICLE", id);
        return toAdmin(post);
    }

    @Transactional
    public AdminArticle setStatus(UUID id, StatusRequest r, UUID actorId) {
        if (r == null || r.status() == null || r.status().isBlank()) throw bad("ARTICLE_STATUS_REQUIRED", "status is required", "status");
        var post = post(id);
        var target = parseStatus(r.status());
        var before = post.getStatus();
        if (BlogPost.PUBLISHED.equals(target)) {
            // micros: what the database stores, so the response matches later reads
            if (r.publishedAt() != null) post.setPublishedAt(r.publishedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            else if (post.getPublishedAt() == null) post.setPublishedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        }
        post.setStatus(target);
        post.stamp(actorId);
        posts.save(post);
        audit.record(PlatformAuditService.Entry.platform(actorId, "ARTICLE_STATUS_" + target, "ARTICLE", id)
            .permission(MANAGE).change(Map.of("status", before), Map.of("status", target)));
        return toAdmin(post);
    }

    @Transactional
    public AdminArticle setFeatured(UUID id, Boolean featured, UUID actorId) {
        if (featured == null) throw bad("ARTICLE_FEATURED_REQUIRED", "featured is required", "featured");
        var post = post(id);
        post.setFeatured(featured);
        post.stamp(actorId);
        posts.save(post);
        recordAudit(actorId, featured ? "ARTICLE_FEATURE" : "ARTICLE_UNFEATURE", "ARTICLE", id);
        return toAdmin(post);
    }

    @Transactional
    public void deleteArticle(UUID id, UUID actorId) {
        var post = post(id);
        post.setDeletedAt(Instant.now());
        post.stamp(actorId);
        posts.save(post);
        recordAudit(actorId, "ARTICLE_DELETE", "ARTICLE", id);
    }

    @Transactional
    public AdminArticle uploadCover(UUID id, MultipartFile file, UUID actorId) {
        var post = post(id);
        if (file == null || file.isEmpty()) throw bad("FILE_REQUIRED", "กรุณาเลือกรูปปก (JPG, PNG หรือ WEBP)", "file");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "ไม่สามารถอ่านไฟล์ได้");
        }
        var stored = storage.storePlatformImage(COVER_FOLDER, bytes, COVER_PURPOSE);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.deleteQuietly(stored.path());
                }
            });
        }
        var name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? stored.storedName() : file.getOriginalFilename();
        if (name.length() > 255) name = name.substring(name.length() - 255);
        var row = new MediaFile(null, actorId, COVER_PURPOSE, stored.path(), name, stored.contentType(), stored.size(), stored.sha256(), null);
        row.makePublic();
        row.stamp(actorId);
        media.save(row);
        replaceCover(post, row.getId());
        post.stamp(actorId);
        posts.save(post);
        recordAudit(actorId, "ARTICLE_COVER_UPLOAD", "ARTICLE", id);
        return toAdmin(post);
    }

    @Transactional
    public AdminArticle removeCover(UUID id, UUID actorId) {
        var post = post(id);
        replaceCover(post, null);
        post.stamp(actorId);
        posts.save(post);
        recordAudit(actorId, "ARTICLE_COVER_REMOVE", "ARTICLE", id);
        return toAdmin(post);
    }

    private void replaceCover(BlogPost post, UUID newMediaId) {
        var old = post.getCoverMediaId();
        post.setCoverMediaId(newMediaId);
        if (old == null) return;
        posts.saveAndFlush(post);
        media.findById(old).filter(m -> COVER_PURPOSE.equals(m.getPurpose())).ifPresent(m -> {
            String path = m.getStorageKey();
            media.delete(m);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) storage.deleteQuietly(path);
                    }
                });
            } else {
                storage.deleteQuietly(path);
            }
        });
    }

    // ===================== Admin: categories =====================

    @Transactional(readOnly = true)
    public List<AdminCategory> adminCategories() {
        return categories.findAllByOrderBySortOrderAscSlugAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional
    public AdminCategory createCategory(CategoryRequest r, UUID actorId) {
        var category = new BlogCategory(actorId);
        applyCategory(category, r);
        category.setSortOrder(r.sortOrder() != null ? r.sortOrder() : categories.maxSortOrder() + 1);
        category.setActive(r.active() == null || r.active());
        categories.save(category);
        recordAudit(actorId, "ARTICLE_CATEGORY_CREATE", "ARTICLE_CATEGORY", category.getId());
        return toAdmin(category);
    }

    @Transactional
    public AdminCategory updateCategory(UUID id, CategoryRequest r, UUID actorId) {
        var category = category(id);
        applyCategory(category, r);
        if (r.sortOrder() != null) category.setSortOrder(r.sortOrder());
        if (r.active() != null) category.setActive(r.active());
        category.stamp(actorId);
        categories.save(category);
        recordAudit(actorId, "ARTICLE_CATEGORY_UPDATE", "ARTICLE_CATEGORY", id);
        return toAdmin(category);
    }

    @Transactional
    public AdminCategory setCategoryActive(UUID id, Boolean active, UUID actorId) {
        if (active == null) throw bad("ARTICLE_ACTIVE_REQUIRED", "active is required", "active");
        var category = category(id);
        category.setActive(active);
        category.stamp(actorId);
        categories.save(category);
        recordAudit(actorId, active ? "ARTICLE_CATEGORY_ACTIVATE" : "ARTICLE_CATEGORY_DEACTIVATE", "ARTICLE_CATEGORY", id);
        return toAdmin(category);
    }

    @Transactional
    public List<AdminCategory> reorderCategories(List<UUID> ids, UUID actorId) {
        reorder(ids, categories::findById, (e, n) -> { e.setSortOrder(n); e.stamp(actorId); }, categories::saveAll);
        recordAudit(actorId, "ARTICLE_CATEGORY_REORDER", "ARTICLE_CATEGORY", ids.get(0));
        return adminCategories();
    }

    @Transactional
    public void deleteCategory(UUID id, UUID actorId) {
        var category = category(id);
        if (posts.countByCategory_IdAndDeletedAtIsNull(id) > 0) {
            throw new AuthException(HttpStatus.CONFLICT, "ARTICLE_CATEGORY_IN_USE", "Category still has articles");
        }
        posts.detachCategory(id);
        categories.delete(categories.findById(id).orElseThrow(ContentService::categoryNotFound));
        recordAudit(actorId, "ARTICLE_CATEGORY_DELETE", "ARTICLE_CATEGORY", id);
    }

    // ===================== Apply / validate =====================

    private void applyArticle(BlogPost post, ArticleRequest r, boolean create) {
        if (r == null) throw bad("ARTICLE_REQUEST_REQUIRED", "Request body is required");
        String slug = requiredSlug(r.slug(), MAX_ARTICLE_SLUG, "ARTICLE_SLUG_INVALID");
        if (RESERVED_SLUG.equals(slug)
            || posts.findBySlug(slug).filter(other -> !other.getId().equals(post.getId())).isPresent()) {
            throw slugTaken();
        }
        String title = requiredText(r.titleTh(), "titleTh", MAX_TITLE);
        String body = requiredText(r.bodyMdTh(), "bodyMdTh", MAX_BODY);
        BlogCategory category = null;
        if (r.categoryId() != null) {
            category = categories.findById(r.categoryId())
                .orElseThrow(() -> bad("ARTICLE_CATEGORY_INVALID", "categoryId does not exist", "categoryId"));
        }
        var tags = normalizeTags(r.tags());
        Long shopId = r.relatedShopId();
        if (shopId != null && shops.findByShopId(shopId).isEmpty()) {
            throw bad("ARTICLE_RELATED_SHOP_INVALID", "relatedShopId does not exist", "relatedShopId");
        }
        var productIds = normalizeProductIds(r.relatedProductIds());
        String audience = r.audience() == null || r.audience().isBlank() ? (create ? "ALL" : post.getAudience()) : parseAudience(r.audience());
        Integer minutes = r.readingMinutes();
        if (minutes != null && (minutes < 1 || minutes > MAX_READING_MINUTES)) {
            throw bad("ARTICLE_READING_MINUTES_INVALID", "readingMinutes must be between 1 and " + MAX_READING_MINUTES, "readingMinutes");
        }

        post.setSlug(slug);
        post.setTitle(title);
        post.setBodyMd(body);
        post.setCategory(category);
        post.setTitleEn(optionalText(r.titleEn(), "titleEn", MAX_TITLE));
        post.setTitleZh(optionalText(r.titleZh(), "titleZh", MAX_TITLE));
        post.setExcerpt(optionalText(r.excerptTh(), "excerptTh", MAX_EXCERPT));
        post.setExcerptEn(optionalText(r.excerptEn(), "excerptEn", MAX_EXCERPT));
        post.setExcerptZh(optionalText(r.excerptZh(), "excerptZh", MAX_EXCERPT));
        post.setBodyMdEn(optionalText(r.bodyMdEn(), "bodyMdEn", MAX_BODY));
        post.setBodyMdZh(optionalText(r.bodyMdZh(), "bodyMdZh", MAX_BODY));
        post.setMetaTitle(optionalText(r.metaTitle(), "metaTitle", MAX_META_TITLE));
        post.setMetaDescription(optionalText(r.metaDescription(), "metaDescription", MAX_META_DESCRIPTION));
        post.setTags(tags);
        post.setRelatedShopId(shopId);
        post.setRelatedProductIds(productIds);
        post.setAudience(audience);
        if (r.featured() != null) post.setFeatured(r.featured());
        if (minutes != null) post.setReadingMinutes(minutes);
        else if (create) post.setReadingMinutes(Math.max(1, (int) Math.ceil(body.length() / 1000.0)));
    }

    private void applyCategory(BlogCategory category, CategoryRequest r) {
        if (r == null) throw bad("ARTICLE_REQUEST_REQUIRED", "Request body is required");
        String slug = requiredSlug(r.slug(), MAX_CATEGORY_SLUG, "ARTICLE_SLUG_INVALID");
        if (categories.findBySlug(slug).filter(other -> !other.getId().equals(category.getId())).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "ARTICLE_CATEGORY_SLUG_TAKEN", "Slug is already in use", "slug");
        }
        category.setSlug(slug);
        category.setNameTh(requiredText(r.nameTh(), "nameTh", MAX_NAME));
        category.setNameEn(optionalText(r.nameEn(), "nameEn", MAX_NAME));
        category.setNameZh(optionalText(r.nameZh(), "nameZh", MAX_NAME));
        category.setDescriptionTh(optionalText(r.descriptionTh(), "descriptionTh", MAX_DESCRIPTION));
        category.setDescriptionEn(optionalText(r.descriptionEn(), "descriptionEn", MAX_DESCRIPTION));
        category.setDescriptionZh(optionalText(r.descriptionZh(), "descriptionZh", MAX_DESCRIPTION));
    }

    private static <E> void reorder(List<UUID> ids, Function<UUID, java.util.Optional<E>> finder,
                                    BiConsumer<E, Integer> setter, Consumer<List<E>> saver) {
        if (ids == null || ids.isEmpty()) throw bad("ARTICLE_REORDER_EMPTY", "ids must not be empty", "ids");
        if (new HashSet<>(ids).size() != ids.size()) throw bad("ARTICLE_REORDER_DUPLICATE", "ids must be unique", "ids");
        var items = new ArrayList<E>(ids.size());
        for (UUID id : ids) {
            if (id == null) throw bad("ARTICLE_REORDER_INVALID", "ids must not contain null", "ids");
            items.add(finder.apply(id).orElseThrow(ContentService::categoryNotFound));
        }
        for (int i = 0; i < items.size(); i++) setter.accept(items.get(i), i + 1);
        saver.accept(items);
    }

    private static List<String> normalizeTags(List<String> raw) {
        if (raw == null) return List.of();
        var out = new LinkedHashSet<String>();
        for (String tag : raw) {
            if (tag == null || tag.isBlank()) continue;
            String t = tag.trim();
            if (t.length() > MAX_TAG) throw bad("ARTICLE_FIELD_TOO_LONG", "each tag must be at most " + MAX_TAG + " characters", "tags");
            out.add(t);
        }
        if (out.size() > MAX_TAGS) throw bad("ARTICLE_TOO_MANY_TAGS", "at most " + MAX_TAGS + " tags", "tags");
        return new ArrayList<>(out);
    }

    private List<UUID> normalizeProductIds(List<UUID> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        var ids = new LinkedHashSet<UUID>();
        for (UUID id : raw) if (id != null) ids.add(id);
        if (ids.size() > MAX_RELATED_PRODUCTS) {
            throw bad("ARTICLE_TOO_MANY_PRODUCTS", "at most " + MAX_RELATED_PRODUCTS + " related products", "relatedProductIds");
        }
        if (products.findAllById(ids).size() != ids.size()) {
            throw bad("ARTICLE_RELATED_PRODUCT_INVALID", "relatedProductIds contains an unknown product", "relatedProductIds");
        }
        return new ArrayList<>(ids);
    }

    // ===================== Mapping =====================

    private ArticleCard toCard(BlogPost p, String l) {
        return new ArticleCard(p.getId(), p.getSlug(),
            pick(l, p.getTitle(), p.getTitleEn(), p.getTitleZh()),
            pick(l, p.getExcerpt(), p.getExcerptEn(), p.getExcerptZh()),
            categoryRef(p.getCategory(), l), coverUrl(p), p.getReadingMinutes(), List.copyOf(p.getTags()),
            p.getAudience(), p.isFeatured(), p.getPublishedAt());
    }

    private static CategoryRef categoryRef(BlogCategory c, String l) {
        return c == null ? null : new CategoryRef(c.getId(), c.getSlug(), pick(l, c.getNameTh(), c.getNameEn(), c.getNameZh()));
    }

    private static String coverUrl(BlogPost p) {
        return p.getCoverMediaId() == null ? null : COVER_URL_PREFIX + p.getCoverMediaId();
    }

    /** Display name only: never e-mail or phone. */
    private String authorName(UUID authorId) {
        if (authorId == null) return null;
        return users.findById(authorId)
            .map(u -> (blankToEmpty(u.getFirstName()) + " " + blankToEmpty(u.getLastName())).trim())
            .filter(name -> !name.isEmpty())
            .orElse(null);
    }

    private RelatedShop relatedShop(BlogPost post, String l) {
        if (post.getRelatedShopId() == null) return null;
        return shops.findByShopId(post.getRelatedShopId())
            .filter(s -> s.getStatus() == ShopStatus.APPROVED)
            .map(s -> new RelatedShop(s.getId(), s.getSlug(), "en".equals(l) && s.getNameEn() != null && !s.getNameEn().isBlank() ? s.getNameEn() : s.getName()))
            .orElse(null);
    }

    private List<RelatedProduct> relatedProducts(BlogPost post, String l) {
        if (post.getRelatedProductIds().isEmpty()) return List.of();
        var byId = new LinkedHashMap<UUID, RelatedProduct>();
        for (var product : products.findAllById(post.getRelatedProductIds())) {
            if (product.getStatus() != ProductStatus.PUBLISHED || product.getDeletedAt() != null) continue;
            if (product.getShop() == null || product.getShop().getStatus() != ShopStatus.APPROVED) continue;
            byId.put(product.getId(), new RelatedProduct(product.getId(), product.getSlug(),
                pick(l, product.getNameTh(), product.getNameEn(), product.getNameZh()), product.getShop().getSlug()));
        }
        var ordered = new ArrayList<RelatedProduct>();
        for (UUID id : post.getRelatedProductIds()) if (byId.containsKey(id)) ordered.add(byId.get(id));
        return ordered;
    }

    private AdminArticleSummary toSummary(BlogPost p) {
        var c = p.getCategory();
        return new AdminArticleSummary(p.getId(), p.getSlug(), p.getStatus(), p.getAudience(), p.isFeatured(),
            c == null ? null : c.getId(), c == null ? null : c.getSlug(), p.getTitle(), p.getTitleEn(), p.getTitleZh(),
            coverUrl(p), p.getReadingMinutes(), List.copyOf(p.getTags()),
            p.getPublishedAt(), p.getCreatedDateTime(), p.getModifiedDateTime());
    }

    private AdminArticle toAdmin(BlogPost p) {
        var c = p.getCategory();
        return new AdminArticle(p.getId(), p.getSlug(), p.getStatus(), p.getAudience(), p.isFeatured(),
            c == null ? null : c.getId(), c == null ? null : c.getSlug(),
            p.getTitle(), p.getTitleEn(), p.getTitleZh(),
            p.getExcerpt(), p.getExcerptEn(), p.getExcerptZh(),
            p.getBodyMd(), p.getBodyMdEn(), p.getBodyMdZh(),
            coverUrl(p), p.getReadingMinutes(), List.copyOf(p.getTags()),
            p.getMetaTitle(), p.getMetaDescription(),
            p.getRelatedShopId(), List.copyOf(p.getRelatedProductIds()),
            p.getAuthorId(), authorName(p.getAuthorId()),
            p.getPublishedAt(), p.getCreatedDateTime(), p.getModifiedDateTime());
    }

    private AdminCategory toAdmin(BlogCategory c) {
        return new AdminCategory(c.getId(), c.getSlug(), c.getSortOrder(), c.isActive(),
            c.getNameTh(), c.getNameEn(), c.getNameZh(),
            c.getDescriptionTh(), c.getDescriptionEn(), c.getDescriptionZh(),
            posts.countByCategory_IdAndDeletedAtIsNull(c.getId()), c.getCreatedDateTime(), c.getModifiedDateTime());
    }

    // ===================== Helpers =====================

    private BlogPost post(UUID id) { return posts.findByIdAndDeletedAtIsNull(id).orElseThrow(ContentService::notFound); }
    private BlogCategory category(UUID id) { return categories.findById(id).orElseThrow(ContentService::categoryNotFound); }

    private void recordAudit(UUID actorId, String action, String resourceType, UUID id) {
        audit.record(PlatformAuditService.Entry.platform(actorId, action, resourceType, id).permission(MANAGE));
    }

    static String normalizeLang(String lang) {
        if (lang == null) return "th";
        String l = lang.trim().toLowerCase(Locale.ROOT);
        if (l.startsWith("en")) return "en";
        if (l.startsWith("zh")) return "zh";
        return "th";
    }

    private static String pick(String lang, String th, String en, String zh) {
        String chosen = switch (lang) { case "en" -> en; case "zh" -> zh; default -> th; };
        return chosen == null || chosen.isBlank() ? th : chosen;
    }

    static String parseAudience(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (!v.equals("ALL") && !v.equals("CUSTOMER") && !v.equals("MERCHANT")) {
            throw bad("ARTICLE_INVALID_AUDIENCE", "audience must be ALL, CUSTOMER or MERCHANT", "audience");
        }
        return v;
    }

    static String parseStatus(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (!v.equals(BlogPost.DRAFT) && !v.equals(BlogPost.PUBLISHED) && !v.equals(BlogPost.ARCHIVED)) {
            throw bad("ARTICLE_INVALID_STATUS", "status must be DRAFT, PUBLISHED or ARCHIVED", "status");
        }
        return v;
    }

    private static String blankToEmpty(String value) { return value == null ? "" : value.trim(); }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String limitQuery(String q) {
        String v = blankToEmpty(q);
        return v.length() > MAX_QUERY ? v.substring(0, MAX_QUERY) : v;
    }

    private static String requiredSlug(String value, int max, String invalidCode) {
        String slug = requiredText(value, "slug", max);
        if (!SLUG.matcher(slug).matches()) throw bad(invalidCode, "slug may only contain a-z, 0-9 and '-'", "slug");
        return slug;
    }

    private static String requiredText(String value, String field, int max) {
        if (value == null || value.isBlank()) throw bad("ARTICLE_FIELD_REQUIRED", field + " is required", field);
        return limited(value.trim(), field, max);
    }

    private static String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        return limited(value.trim(), field, max);
    }

    private static String limited(String value, String field, int max) {
        if (value.length() > max) throw bad("ARTICLE_FIELD_TOO_LONG", field + " must be at most " + max + " characters", field);
        return value;
    }

    private static AuthException bad(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static AuthException bad(String code, String message, String field) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message, field);
    }

    private static AuthException slugTaken() {
        return new AuthException(HttpStatus.CONFLICT, "ARTICLE_SLUG_TAKEN", "Slug is already in use", "slug");
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "ARTICLE_NOT_FOUND", "Article not found");
    }

    private static AuthException categoryNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "ARTICLE_CATEGORY_NOT_FOUND", "Article category not found");
    }

    private static AuthException coverNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "ARTICLE_COVER_NOT_FOUND", "Cover image not found");
    }
}
