package com.thaishopfun.help;

import com.thaishopfun.audit.PlatformAuditService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.help.HelpDtos.*;
import com.thaishopfun.merchant.MerchantStorageService;
import com.thaishopfun.order.MediaFile;
import com.thaishopfun.order.MediaFileRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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

import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Help center: localized public reads and permission-guarded admin CRUD / ordering (audited). */
@Service
public class HelpService {
    static final String MANAGE = "PLATFORM_HELP_MANAGE";
    static final String IMAGE_PURPOSE = "HELP_IMAGE";
    static final String IMAGE_FOLDER = "help-images";
    static final String IMAGE_URL_PREFIX = "/api/help/images/";
    static final int FAQ_SEARCH_LIMIT = 50;
    static final int TOPIC_FAQ_LIMIT = 200;
    private static final int MAX_SESSION_KEY = 64;
    private static final int MAX_SLUG = 80, MAX_ICON = 64, MAX_TITLE = 200, MAX_SHORT = 500;
    private static final Set<String> NOT_HELPFUL_REASONS = Set.of(
        "HARD_TO_UNDERSTAND",
        "INCORRECT_OR_INCOMPLETE",
        "DID_NOT_SOLVE",
        "BAD_LINKS",
        "POLICY_FRICTION"
    );

    private final HelpTopicRepository topics;
    private final HelpFaqRepository faqs;
    private final HelpGuideRepository guides;
    private final HelpFeedbackRepository feedback;
    private final MediaFileRepository media;
    private final MerchantStorageService storage;
    private final PlatformAuditService audit;

    public HelpService(HelpTopicRepository topics, HelpFaqRepository faqs, HelpGuideRepository guides,
                       HelpFeedbackRepository feedback, MediaFileRepository media,
                       MerchantStorageService storage, PlatformAuditService audit) {
        this.topics = topics;
        this.faqs = faqs;
        this.guides = guides;
        this.feedback = feedback;
        this.media = media;
        this.storage = storage;
        this.audit = audit;
    }

    // ===================== Public =====================

    @Transactional(readOnly = true)
    public List<TopicView> listTopics(String lang) {
        String l = normalizeLang(lang);
        return topics.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(t -> new TopicView(t.getId(), t.getSlug(), t.getIcon(),
                pick(l, t.getTitleTh(), t.getTitleEn(), t.getTitleZh()),
                pick(l, t.getDescriptionTh(), t.getDescriptionEn(), t.getDescriptionZh()),
                faqs.countByTopic_IdAndActiveTrue(t.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FaqView> listFaqs(String lang, String topicSlug, String q, String audience) {
        String l = normalizeLang(lang);
        String aud = "";
        if (audience != null && !audience.isBlank()) aud = parseAudience(audience).name();
        return faqs.search(blankToEmpty(topicSlug), aud, blankToEmpty(q), PageRequest.of(0, FAQ_SEARCH_LIMIT)).stream()
            .map(f -> toView(f, l))
            .toList();
    }

    @Transactional(readOnly = true)
    public TopicDetailView topicDetail(String slug, String lang) {
        String l = normalizeLang(lang);
        var topic = topics.findBySlug(blankToEmpty(slug)).filter(HelpTopic::isActive).orElseThrow(HelpService::notFound);
        var items = faqs.search(topic.getSlug(), "", "", PageRequest.of(0, TOPIC_FAQ_LIMIT)).stream().map(f -> toView(f, l)).toList();
        return new TopicDetailView(topic.getId(), topic.getSlug(), topic.getIcon(),
            pick(l, topic.getTitleTh(), topic.getTitleEn(), topic.getTitleZh()),
            pick(l, topic.getDescriptionTh(), topic.getDescriptionEn(), topic.getDescriptionZh()), items);
    }

    @Transactional(readOnly = true)
    public FaqView faqDetail(UUID id, String lang) {
        return toView(faqs.findPublicById(id).orElseThrow(HelpService::notFound), normalizeLang(lang));
    }

    /**
     * One vote per signed-in user or anonymous session. Repeating the same vote is a no-op.
     * Changing the vote adjusts counters. Not-helpful votes require a reason code.
     */
    @Transactional
    public FeedbackResult submitFeedback(UUID faqId, FeedbackRequest r, UUID userId) {
        if (r == null || r.helpful() == null) throw bad("HELP_HELPFUL_REQUIRED", "helpful is required", "helpful");
        faqs.findPublicById(faqId).orElseThrow(HelpService::notFound);
        String session = r.sessionKey() == null ? null : r.sessionKey().trim();
        if (session != null && session.isEmpty()) session = null;
        if (userId == null) {
            if (session == null) throw bad("HELP_FEEDBACK_IDENTITY_REQUIRED", "sessionKey is required when not signed in", "sessionKey");
            if (session.length() > MAX_SESSION_KEY) throw bad("HELP_FIELD_TOO_LONG", "sessionKey must be at most " + MAX_SESSION_KEY + " characters", "sessionKey");
        }
        String reason = normalizeReason(r.helpful(), r.reasonCode());
        var existing = userId != null
            ? feedback.findByFaqIdAndUserId(faqId, userId)
            : feedback.findByFaqIdAndSessionKeyAndUserIdIsNull(faqId, session);
        if (existing.isPresent()) {
            var row = existing.get();
            boolean sameVote = row.isHelpful() == r.helpful();
            boolean sameReason = (row.getReasonCode() == null && reason == null)
                || (row.getReasonCode() != null && row.getReasonCode().equals(reason));
            if (sameVote && sameReason) {
                var faq = faqs.findById(faqId).orElseThrow(HelpService::notFound);
                return new FeedbackResult(faqId, false, faq.getHelpfulYes(), faq.getHelpfulNo());
            }
            if (!sameVote) {
                if (row.isHelpful()) faqs.decrementHelpfulYes(faqId); else faqs.decrementHelpfulNo(faqId);
                if (r.helpful()) faqs.incrementHelpfulYes(faqId); else faqs.incrementHelpfulNo(faqId);
            }
            row.updateVote(r.helpful(), reason);
            feedback.saveAndFlush(row);
        } else {
            feedback.saveAndFlush(new HelpFeedback(faqId, r.helpful(), userId, session, reason));
            if (r.helpful()) faqs.incrementHelpfulYes(faqId); else faqs.incrementHelpfulNo(faqId);
        }
        var fresh = faqs.findById(faqId).orElseThrow(HelpService::notFound);
        return new FeedbackResult(faqId, true, fresh.getHelpfulYes(), fresh.getHelpfulNo());
    }

    private static String normalizeReason(boolean helpful, String reasonCode) {
        if (helpful) return null;
        if (reasonCode == null || reasonCode.isBlank()) {
            throw bad("HELP_REASON_REQUIRED", "reasonCode is required when helpful=false", "reasonCode");
        }
        String code = reasonCode.trim().toUpperCase(Locale.ROOT);
        if (!NOT_HELPFUL_REASONS.contains(code)) {
            throw bad("HELP_REASON_INVALID", "reasonCode is not allowed", "reasonCode");
        }
        return code;
    }

    private static FaqView toView(HelpFaq f, String l) {
        return new FaqView(f.getId(), f.getTopic().getSlug(), f.getAudience(),
            pick(l, f.getQuestionTh(), f.getQuestionEn(), f.getQuestionZh()),
            pick(l, f.getAnswerTh(), f.getAnswerEn(), f.getAnswerZh()),
            f.getHelpfulYes(), f.getHelpfulNo(), f.getLastReviewedAt());
    }

    @Transactional(readOnly = true)
    public List<GuideView> listGuides(String lang) {
        String l = normalizeLang(lang);
        return guides.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(g -> new GuideView(g.getId(), g.getSlug(),
                pick(l, g.getTitleTh(), g.getTitleEn(), g.getTitleZh()),
                pick(l, g.getSummaryTh(), g.getSummaryEn(), g.getSummaryZh()),
                g.getUrl()))
            .toList();
    }

    // ===================== Admin: topics =====================

    @Transactional(readOnly = true)
    public List<AdminTopic> adminTopics() {
        return topics.findAllByOrderBySortOrderAscCreatedAtAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional
    public AdminTopic createTopic(TopicRequest r, UUID actorId) {
        var topic = new HelpTopic(actorId);
        applyTopic(topic, r);
        topic.setSortOrder(r.sortOrder() != null ? r.sortOrder() : topics.maxSortOrder() + 1);
        topic.setActive(r.active() == null || r.active());
        topics.save(topic);
        recordAudit(actorId, "HELP_TOPIC_CREATE", "HELP_TOPIC", topic.getId());
        return toAdmin(topic);
    }

    @Transactional
    public AdminTopic updateTopic(UUID id, TopicRequest r, UUID actorId) {
        var topic = topic(id);
        applyTopic(topic, r);
        if (r.sortOrder() != null) topic.setSortOrder(r.sortOrder());
        if (r.active() != null) topic.setActive(r.active());
        topic.touch(actorId);
        topics.save(topic);
        recordAudit(actorId, "HELP_TOPIC_UPDATE", "HELP_TOPIC", id);
        return toAdmin(topic);
    }

    @Transactional
    public void deleteTopic(UUID id, UUID actorId) {
        var topic = topic(id);
        if (faqs.countByTopic_Id(id) > 0) {
            throw new AuthException(HttpStatus.CONFLICT, "HELP_TOPIC_IN_USE", "Topic still has FAQs");
        }
        topics.delete(topic);
        recordAudit(actorId, "HELP_TOPIC_DELETE", "HELP_TOPIC", id);
    }

    @Transactional
    public AdminTopic setTopicActive(UUID id, Boolean active, UUID actorId) {
        var topic = topic(id);
        topic.setActive(requireActive(active));
        topic.touch(actorId);
        topics.save(topic);
        recordAudit(actorId, topic.isActive() ? "HELP_TOPIC_ACTIVATE" : "HELP_TOPIC_DEACTIVATE", "HELP_TOPIC", id);
        return toAdmin(topic);
    }

    @Transactional
    public List<AdminTopic> reorderTopics(List<UUID> ids, UUID actorId) {
        reorder(ids, topics::findById, (e, n) -> { e.setSortOrder(n); e.touch(actorId); }, topics::saveAll);
        recordAudit(actorId, "HELP_TOPIC_REORDER", "HELP_TOPIC", ids.get(0));
        return adminTopics();
    }

    // ===================== Admin: FAQs =====================

    @Transactional(readOnly = true)
    public List<AdminFaq> adminFaqs() {
        return faqs.findAllWithTopic().stream().map(this::toAdmin).toList();
    }

    @Transactional
    public AdminFaq createFaq(FaqRequest r, UUID actorId) {
        var faq = new HelpFaq(actorId);
        applyFaq(faq, r);
        faq.setSortOrder(r.sortOrder() != null ? r.sortOrder() : faqs.maxSortOrder() + 1);
        faq.setActive(r.active() == null || r.active());
        faqs.save(faq);
        recordAudit(actorId, "HELP_FAQ_CREATE", "HELP_FAQ", faq.getId());
        return toAdmin(faq);
    }

    @Transactional
    public AdminFaq updateFaq(UUID id, FaqRequest r, UUID actorId) {
        var faq = faq(id);
        applyFaq(faq, r);
        if (r.sortOrder() != null) faq.setSortOrder(r.sortOrder());
        if (r.active() != null) faq.setActive(r.active());
        faq.touch(actorId);
        faqs.save(faq);
        recordAudit(actorId, "HELP_FAQ_UPDATE", "HELP_FAQ", id);
        return toAdmin(faq);
    }

    @Transactional
    public void deleteFaq(UUID id, UUID actorId) {
        faqs.delete(faq(id));
        recordAudit(actorId, "HELP_FAQ_DELETE", "HELP_FAQ", id);
    }

    /** Stamps last_reviewed_at = now (content owner confirmed the answer is still accurate). */
    @Transactional
    public AdminFaq markFaqReviewed(UUID id, UUID actorId) {
        var faq = faq(id);
        faq.markReviewed(actorId);
        faqs.save(faq);
        recordAudit(actorId, "HELP_FAQ_REVIEWED", "HELP_FAQ", id);
        return toAdmin(faq);
    }

    @Transactional
    public AdminFaq setFaqActive(UUID id, Boolean active, UUID actorId) {
        var faq = faq(id);
        faq.setActive(requireActive(active));
        faq.touch(actorId);
        faqs.save(faq);
        recordAudit(actorId, faq.isActive() ? "HELP_FAQ_ACTIVATE" : "HELP_FAQ_DEACTIVATE", "HELP_FAQ", id);
        return toAdmin(faq);
    }

    @Transactional
    public List<AdminFaq> reorderFaqs(List<UUID> ids, UUID actorId) {
        reorder(ids, faqs::findById, (e, n) -> { e.setSortOrder(n); e.touch(actorId); }, faqs::saveAll);
        recordAudit(actorId, "HELP_FAQ_REORDER", "HELP_FAQ", ids.get(0));
        return adminFaqs();
    }

    // ===================== Admin: guides =====================

    @Transactional(readOnly = true)
    public List<AdminGuide> adminGuides() {
        return guides.findAllByOrderBySortOrderAscCreatedAtAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional
    public AdminGuide createGuide(GuideRequest r, UUID actorId) {
        var guide = new HelpGuide(actorId);
        applyGuide(guide, r);
        guide.setSortOrder(r.sortOrder() != null ? r.sortOrder() : guides.maxSortOrder() + 1);
        guide.setActive(r.active() == null || r.active());
        guides.save(guide);
        recordAudit(actorId, "HELP_GUIDE_CREATE", "HELP_GUIDE", guide.getId());
        return toAdmin(guide);
    }

    @Transactional
    public AdminGuide updateGuide(UUID id, GuideRequest r, UUID actorId) {
        var guide = guide(id);
        applyGuide(guide, r);
        if (r.sortOrder() != null) guide.setSortOrder(r.sortOrder());
        if (r.active() != null) guide.setActive(r.active());
        guide.touch(actorId);
        guides.save(guide);
        recordAudit(actorId, "HELP_GUIDE_UPDATE", "HELP_GUIDE", id);
        return toAdmin(guide);
    }

    @Transactional
    public void deleteGuide(UUID id, UUID actorId) {
        guides.delete(guide(id));
        recordAudit(actorId, "HELP_GUIDE_DELETE", "HELP_GUIDE", id);
    }

    @Transactional
    public AdminGuide setGuideActive(UUID id, Boolean active, UUID actorId) {
        var guide = guide(id);
        guide.setActive(requireActive(active));
        guide.touch(actorId);
        guides.save(guide);
        recordAudit(actorId, guide.isActive() ? "HELP_GUIDE_ACTIVATE" : "HELP_GUIDE_DEACTIVATE", "HELP_GUIDE", id);
        return toAdmin(guide);
    }

    @Transactional
    public List<AdminGuide> reorderGuides(List<UUID> ids, UUID actorId) {
        reorder(ids, guides::findById, (e, n) -> { e.setSortOrder(n); e.touch(actorId); }, guides::saveAll);
        recordAudit(actorId, "HELP_GUIDE_REORDER", "HELP_GUIDE", ids.get(0));
        return adminGuides();
    }

    // ===================== Images =====================

    @Transactional
    public HelpImageUpload uploadImage(MultipartFile file, UUID actorId) {
        if (file == null || file.isEmpty()) throw bad("FILE_REQUIRED", "กรุณาเลือกรูป (JPG, PNG หรือ WEBP)", "file");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "ไม่สามารถอ่านไฟล์ได้", "file");
        }
        var stored = storage.storePlatformImage(IMAGE_FOLDER, bytes, IMAGE_PURPOSE);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.deleteQuietly(stored.path());
                }
            });
        }
        var name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
            ? stored.storedName() : file.getOriginalFilename();
        if (name.length() > 255) name = name.substring(name.length() - 255);
        var row = new MediaFile(null, actorId, IMAGE_PURPOSE, stored.path(), name, stored.contentType(), stored.size(), stored.sha256(), null);
        row.makePublic();
        row.stamp(actorId);
        media.save(row);
        recordAudit(actorId, "HELP_IMAGE_UPLOAD", "HELP_IMAGE", row.getId());
        return new HelpImageUpload(row.getId(), IMAGE_URL_PREFIX + row.getId());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> serveImage(UUID mediaId) {
        var file = media.findById(mediaId)
            .filter(m -> IMAGE_PURPOSE.equals(m.getPurpose()) && "PUBLIC".equals(m.getVisibility()))
            .orElseThrow(HelpService::imageNotFound);
        var path = storage.openPublicImage(file.getStorageKey()).orElseThrow(HelpService::imageNotFound);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(new FileSystemResource(path));
    }

    // ===================== Apply / validate =====================

    private void applyTopic(HelpTopic topic, TopicRequest r) {
        if (r == null) throw bad("HELP_REQUEST_REQUIRED", "Request body is required");
        String slug = requiredText(r.slug(), "slug", MAX_SLUG);
        if (topics.findBySlug(slug).filter(other -> !other.getId().equals(topic.getId())).isPresent()) throw slugTaken();
        topic.setSlug(slug);
        topic.setIcon(requiredText(r.icon(), "icon", MAX_ICON));
        topic.setTitleTh(requiredText(r.titleTh(), "titleTh", MAX_TITLE));
        topic.setTitleEn(optionalText(r.titleEn(), "titleEn", MAX_TITLE));
        topic.setTitleZh(optionalText(r.titleZh(), "titleZh", MAX_TITLE));
        topic.setDescriptionTh(HelpHtml.required(r.descriptionTh(), "descriptionTh"));
        topic.setDescriptionEn(HelpHtml.optional(r.descriptionEn(), "descriptionEn"));
        topic.setDescriptionZh(HelpHtml.optional(r.descriptionZh(), "descriptionZh"));
    }

    private void applyFaq(HelpFaq faq, FaqRequest r) {
        if (r == null) throw bad("HELP_REQUEST_REQUIRED", "Request body is required");
        if (r.topicId() == null) throw bad("HELP_TOPIC_REQUIRED", "topicId is required", "topicId");
        faq.setTopic(topic(r.topicId()));
        faq.setAudience(r.audience() == null || r.audience().isBlank() ? HelpAudience.ALL : parseAudience(r.audience()));
        faq.setQuestionTh(requiredText(r.questionTh(), "questionTh", MAX_SHORT));
        faq.setQuestionEn(optionalText(r.questionEn(), "questionEn", MAX_SHORT));
        faq.setQuestionZh(optionalText(r.questionZh(), "questionZh", MAX_SHORT));
        faq.setAnswerTh(HelpHtml.required(r.answerTh(), "answerTh"));
        faq.setAnswerEn(HelpHtml.optional(r.answerEn(), "answerEn"));
        faq.setAnswerZh(HelpHtml.optional(r.answerZh(), "answerZh"));
    }

    private void applyGuide(HelpGuide guide, GuideRequest r) {
        if (r == null) throw bad("HELP_REQUEST_REQUIRED", "Request body is required");
        String slug = requiredText(r.slug(), "slug", MAX_SLUG);
        if (guides.findBySlug(slug).filter(other -> !other.getId().equals(guide.getId())).isPresent()) throw slugTaken();
        guide.setSlug(slug);
        guide.setTitleTh(requiredText(r.titleTh(), "titleTh", MAX_TITLE));
        guide.setTitleEn(optionalText(r.titleEn(), "titleEn", MAX_TITLE));
        guide.setTitleZh(optionalText(r.titleZh(), "titleZh", MAX_TITLE));
        guide.setSummaryTh(HelpHtml.required(r.summaryTh(), "summaryTh"));
        guide.setSummaryEn(HelpHtml.optional(r.summaryEn(), "summaryEn"));
        guide.setSummaryZh(HelpHtml.optional(r.summaryZh(), "summaryZh"));
        guide.setUrl(optionalText(r.url(), "url", MAX_SHORT));
    }

    private static <E> void reorder(List<UUID> ids, java.util.function.Function<UUID, java.util.Optional<E>> finder,
                                    java.util.function.BiConsumer<E, Integer> setter,
                                    java.util.function.Consumer<List<E>> saver) {
        if (ids == null || ids.isEmpty()) throw bad("HELP_REORDER_EMPTY", "ids must not be empty", "ids");
        if (new HashSet<>(ids).size() != ids.size()) throw bad("HELP_REORDER_DUPLICATE", "ids must be unique", "ids");
        var items = new ArrayList<E>(ids.size());
        for (UUID id : ids) {
            if (id == null) throw bad("HELP_REORDER_INVALID", "ids must not contain null", "ids");
            items.add(finder.apply(id).orElseThrow(HelpService::notFound));
        }
        for (int i = 0; i < items.size(); i++) setter.accept(items.get(i), i + 1);
        saver.accept(items);
    }

    // ===================== Helpers =====================

    private HelpTopic topic(UUID id) { return topics.findById(id).orElseThrow(HelpService::notFound); }
    private HelpFaq faq(UUID id) { return faqs.findById(id).orElseThrow(HelpService::notFound); }
    private HelpGuide guide(UUID id) { return guides.findById(id).orElseThrow(HelpService::notFound); }

    private void recordAudit(UUID actorId, String action, String resourceType, UUID id) {
        audit.record(PlatformAuditService.Entry.platform(actorId, action, resourceType, id).permission(MANAGE));
    }

    private static boolean requireActive(Boolean active) {
        if (active == null) throw bad("HELP_ACTIVE_REQUIRED", "active is required", "active");
        return active;
    }

    static HelpAudience parseAudience(String value) {
        try {
            return HelpAudience.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("HELP_INVALID_AUDIENCE", "audience must be ALL, CUSTOMER or MERCHANT", "audience");
        }
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

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requiredText(String value, String field, int max) {
        if (value == null || value.isBlank()) throw bad("HELP_FIELD_REQUIRED", field + " is required", field);
        return limited(value.trim(), field, max);
    }

    private static String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        return limited(value.trim(), field, max);
    }

    private static String limited(String value, String field, int max) {
        if (value.length() > max) throw bad("HELP_FIELD_TOO_LONG", field + " must be at most " + max + " characters", field);
        return value;
    }

    private static AuthException bad(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static AuthException bad(String code, String message, String field) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message, field);
    }

    private static AuthException slugTaken() {
        return new AuthException(HttpStatus.CONFLICT, "HELP_SLUG_TAKEN", "Slug is already in use", "slug");
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "HELP_NOT_FOUND", "Help item not found");
    }

    private static AuthException imageNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "HELP_IMAGE_NOT_FOUND", "Help image not found");
    }

    private AdminTopic toAdmin(HelpTopic t) {
        return new AdminTopic(t.getId(), t.getSlug(), t.getIcon(), t.getSortOrder(), t.isActive(),
            t.getTitleTh(), t.getTitleEn(), t.getTitleZh(),
            t.getDescriptionTh(), t.getDescriptionEn(), t.getDescriptionZh(),
            (int) faqs.countByTopic_Id(t.getId()), t.getCreatedAt(), t.getUpdatedAt(), t.getUpdatedBy());
    }

    private AdminFaq toAdmin(HelpFaq f) {
        return new AdminFaq(f.getId(), f.getTopic().getId(), f.getTopic().getSlug(), f.getSortOrder(), f.isActive(), f.getAudience(),
            f.getQuestionTh(), f.getQuestionEn(), f.getQuestionZh(),
            f.getAnswerTh(), f.getAnswerEn(), f.getAnswerZh(),
            f.getHelpfulYes(), f.getHelpfulNo(), f.getLastReviewedAt(),
            f.getCreatedAt(), f.getUpdatedAt(), f.getUpdatedBy());
    }

    private AdminGuide toAdmin(HelpGuide g) {
        return new AdminGuide(g.getId(), g.getSlug(), g.getSortOrder(), g.isActive(),
            g.getTitleTh(), g.getTitleEn(), g.getTitleZh(),
            g.getSummaryTh(), g.getSummaryEn(), g.getSummaryZh(), g.getUrl(),
            g.getCreatedAt(), g.getUpdatedAt(), g.getUpdatedBy());
    }
}
