package com.thaishopfun.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TranslationService {
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final List<String> TARGET_LOCALES = List.of("en", "zh");

    private final ContentTranslationRepository repository;
    private final TranslationProvider provider;
    private final TranslationProperties properties;
    private final ApplicationEventPublisher events;
    private final EntityThaiSourceRegistry thaiSources;
    private final AtomicBoolean backfillRunning = new AtomicBoolean(false);
    private final AtomicBoolean missingProviderLogged = new AtomicBoolean(false);

    public TranslationService(
        ContentTranslationRepository repository,
        TranslationProvider provider,
        TranslationProperties properties,
        ApplicationEventPublisher events,
        EntityThaiSourceRegistry thaiSources
    ) {
        this.repository = repository;
        this.provider = provider;
        this.properties = properties;
        this.events = events;
        this.thaiSources = thaiSources;
    }

    @Transactional
    public void requestTranslation(TranslationEntityType entityType, String entityId, Map<String, String> thaiFields) {
        if (entityType == null || entityId == null || thaiFields == null || thaiFields.isEmpty()) return;
        var changed = false;
        for (var entry : thaiFields.entrySet()) {
            var field = entry.getKey();
            var thai = TranslationTextSupport.blankToNull(entry.getValue());
            if (thai == null) continue;
            var hash = TranslationTextSupport.hashThai(thai);
            for (var locale : TARGET_LOCALES) {
                var existing = repository.findByEntityTypeAndEntityIdAndFieldNameAndLocale(entityType, entityId, field, locale).orElse(null);
                if (existing != null && existing.getOrigin().manual() && hash.equals(existing.getSourceHash())) {
                    continue;
                }
                if (existing != null
                    && existing.getStatus() == TranslationStatus.DONE
                    && hash.equals(existing.getSourceHash())
                    && existing.getOrigin() == TranslationOrigin.MACHINE) {
                    continue;
                }
                if (existing == null) {
                    existing = new ContentTranslation(entityType, entityId, field, locale, TranslationOrigin.MACHINE, TranslationStatus.PENDING);
                }
                existing.setOrigin(TranslationOrigin.MACHINE);
                existing.setStatus(TranslationStatus.PENDING);
                existing.setSourceHash(hash);
                existing.setTranslatedText(null);
                existing.setErrorMessage(null);
                existing.setProvider(null);
                existing.setAttempts(0);
                repository.save(existing);
                changed = true;
            }
        }
        if (changed) {
            events.publishEvent(new TranslationRequestedEvent(entityType, entityId));
        }
    }

    @Transactional
    public void recordHuman(TranslationEntityType entityType, String entityId, String fieldName, String locale, String thaiSource, String translated) {
        var text = TranslationTextSupport.blankToNull(translated);
        if (text == null) {
            repository.findByEntityTypeAndEntityIdAndFieldNameAndLocale(entityType, entityId, fieldName, locale)
                .filter(row -> row.getOrigin().manual())
                .ifPresent(repository::delete);
            return;
        }
        var row = repository.findByEntityTypeAndEntityIdAndFieldNameAndLocale(entityType, entityId, fieldName, locale)
            .orElseGet(() -> new ContentTranslation(entityType, entityId, fieldName, locale, TranslationOrigin.MANUAL, TranslationStatus.DONE));
        row.setOrigin(TranslationOrigin.MANUAL);
        row.setStatus(TranslationStatus.DONE);
        row.setTranslatedText(text);
        row.setSourceHash(TranslationTextSupport.hashThai(thaiSource));
        row.setErrorMessage(null);
        row.setProvider("human");
        repository.save(row);
    }

    /** Removes every stored translation (machine or manual, all target locales) for one field. */
    @Transactional
    public void clearField(TranslationEntityType entityType, String entityId, String fieldName) {
        if (entityType == null || entityId == null || fieldName == null) return;
        for (var locale : TARGET_LOCALES) {
            repository.findByEntityTypeAndEntityIdAndFieldNameAndLocale(entityType, entityId, fieldName, locale)
                .ifPresent(repository::delete);
        }
    }

    @Transactional
    public void revertToMachine(TranslationEntityType entityType, String entityId, String fieldName, String locale, String thaiSource) {
        repository.findByEntityTypeAndEntityIdAndFieldNameAndLocale(entityType, entityId, fieldName, locale)
            .ifPresent(row -> {
                if (!row.getOrigin().manual()) return;
                repository.delete(row);
            });
        if (thaiSource == null || thaiSource.isBlank()) return;
        requestTranslation(entityType, entityId, Map.of(fieldName, thaiSource));
    }

    @Transactional
    public void retry(TranslationEntityType entityType, String entityId, String fieldName, String locale) {
        var row = repository.findByEntityTypeAndEntityIdAndFieldNameAndLocale(entityType, entityId, fieldName, locale).orElse(null);
        if (row == null) throw new RetryRejected("TRANSLATION_NOT_FOUND", "ไม่พบงานแปลของฟิลด์นี้");
        if (row.getStatus() != TranslationStatus.FAILED && row.getStatus() != TranslationStatus.PENDING) {
            throw new RetryRejected("TRANSLATION_NOT_RETRYABLE", "เข้าคิวใหม่ได้เฉพาะงานที่กำลังรอหรือแปลไม่สำเร็จ สถานะปัจจุบันคือ " + row.getStatus());
        }
        row.setStatus(TranslationStatus.PENDING);
        row.setAttempts(0);
        row.setErrorMessage(null);
        row.setOrigin(TranslationOrigin.MACHINE);
        repository.save(row);
        events.publishEvent(new TranslationRequestedEvent(entityType, entityId));
    }

    public static final class RetryRejected extends RuntimeException {
        private final String code;

        public RetryRejected(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() { return code; }
    }

    @Transactional
    public void dropUnlisted(TranslationEntityType entityType, String entityId, Collection<String> fieldNames) {
        if (entityId == null) return;
        var keep = fieldNames == null ? List.<String>of() : fieldNames;
        for (var row : repository.findByEntityTypeAndEntityIdIn(entityType, List.of(entityId))) {
            if (!keep.contains(row.getFieldName())) repository.delete(row);
        }
    }

    @Transactional(readOnly = true)
    public ContentTranslationIndex loadIndex(TranslationEntityType entityType, List<String> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return ContentTranslationIndex.of(List.of());
        return ContentTranslationIndex.of(repository.findByEntityTypeAndEntityIdIn(entityType, entityIds));
    }

    /**
     * Live preview for the merchant product form (no persistence). Translates Thai field values into the
     * requested locales using the active provider. Blank sources stay blank; missing provider returns empty strings.
     */
    public Map<String, Map<String, String>> preview(Map<String, String> fields, List<String> locales) {
        var targets = new ArrayList<String>();
        if (locales != null) {
            for (var locale : locales) {
                if (locale == null) continue;
                var normalized = locale.trim().toLowerCase();
                if (TARGET_LOCALES.contains(normalized) && !targets.contains(normalized)) targets.add(normalized);
            }
        }
        if (targets.isEmpty()) targets.addAll(TARGET_LOCALES);

        var keys = new ArrayList<String>();
        var sources = new ArrayList<String>();
        if (fields != null) {
            for (var entry : fields.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                var thai = TranslationTextSupport.blankToNull(entry.getValue());
                keys.add(entry.getKey().trim());
                sources.add(thai == null ? "" : thai);
            }
        }

        var out = new LinkedHashMap<String, Map<String, String>>();
        for (var locale : targets) {
            var row = new LinkedHashMap<String, String>();
            for (var key : keys) row.put(key, "");
            out.put(locale, row);
        }
        if (keys.isEmpty()) return out;

        if (!provider.configured()) {
            log.debug("translation preview skipped — provider {} not configured", provider.id());
            return out;
        }

        for (var locale : targets) {
            try {
                var translated = provider.translate(sources, "th", locale);
                var row = out.get(locale);
                for (int index = 0; index < keys.size(); index++) {
                    var source = sources.get(index);
                    if (source == null || source.isBlank()) {
                        row.put(keys.get(index), "");
                        continue;
                    }
                    var value = index < translated.size() ? translated.get(index) : null;
                    row.put(keys.get(index), value == null ? "" : value);
                }
            } catch (Exception exception) {
                log.warn("translation preview failed for {} via {} — {}", locale, provider.id(), exception.getMessage());
                for (var key : keys) out.get(locale).put(key, "");
            }
        }
        return out;
    }

    /** false in tests: queue is processed explicitly via processPendingForTests() instead of racing a background thread. */
    @Value("${app.translation.process-after-commit:true}")
    private boolean processAfterCommit = true;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void afterCommit(TranslationRequestedEvent event) {
        if (!processAfterCommit) return;
        try {
            processEntityQueue(event.entityType(), event.entityId());
        } catch (Exception exception) {
            log.warn("translation after-commit failed for {}:{} — {}", event.entityType(), event.entityId(), exception.getMessage());
        }
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.translation.poll-delay-ms:60000}")
    public void scheduledPoll() {
        try {
            processGlobalQueue();
        } catch (Exception exception) {
            log.warn("translation poll failed — {}", exception.getMessage());
        }
    }

    @Transactional
    public void processPendingForTests() {
        processGlobalQueue();
    }

    @Transactional
    public BackfillResult backfill(TranslationEntityType entityType, BackfillSource source) {
        if (!backfillRunning.compareAndSet(false, true)) {
            return new BackfillResult(0, 0, true);
        }
        int queued = 0;
        int skipped = 0;
        try {
            for (var item : source.items(entityType)) {
                var before = repository.findByEntityTypeAndEntityIdIn(entityType, List.of(item.entityId())).size();
                requestTranslation(entityType, item.entityId(), item.fields());
                var after = repository.findByEntityTypeAndEntityIdIn(entityType, List.of(item.entityId())).size();
                if (after >= before) queued++;
                else skipped++;
            }
        } finally {
            backfillRunning.set(false);
        }
        processGlobalQueue();
        return new BackfillResult(queued, skipped, false);
    }

    private void processEntityQueue(TranslationEntityType entityType, String entityId) {
        var queue = repository.findWorkQueueForEntity(
            entityType,
            entityId,
            List.of(TranslationStatus.PENDING, TranslationStatus.FAILED),
            properties.maxAttempts()
        );
        translateQueue(queue);
    }

    private void processGlobalQueue() {
        var queue = repository.findWorkQueue(
            List.of(TranslationStatus.PENDING, TranslationStatus.FAILED),
            properties.maxAttempts()
        );
        translateQueue(queue);
    }

    private void translateQueue(List<ContentTranslation> queue) {
        if (queue.isEmpty() || !providerReady()) return;
        var batches = new LinkedHashMap<String, List<ContentTranslation>>();
        for (var row : queue) {
            if (!readyToRetry(row)) continue;
            batches.computeIfAbsent(row.getLocale(), ignored -> new ArrayList<>()).add(row);
        }
        for (var entry : batches.entrySet()) {
            var locale = entry.getKey();
            var rows = entry.getValue();
            for (int start = 0; start < rows.size(); start += properties.batchSize()) {
                var slice = rows.subList(start, Math.min(start + properties.batchSize(), rows.size()));
                translateBatch(slice, locale);
            }
        }
    }

    private void translateBatch(List<ContentTranslation> rows, String locale) {
        for (var row : rows) {
            row.setAttempts(row.getAttempts() + 1);
        }
        var thaiTexts = rows.stream().map(this::lookupThai).toList();
        if (thaiTexts.stream().allMatch(text -> text == null || text.isBlank())) {
            markFailed(rows, "missing thai source");
            return;
        }
        try {
            var target = ContentLocale.targetCode(locale);
            var translated = provider.translate(thaiTexts, "th", target);
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                var text = translated.size() > i ? translated.get(i) : null;
                if (text == null || text.isBlank()) {
                    row.setErrorMessage("empty translation");
                    row.setStatus(row.getAttempts() >= properties.maxAttempts() ? TranslationStatus.FAILED : TranslationStatus.PENDING);
                } else {
                    row.setTranslatedText(text.trim());
                    row.setStatus(TranslationStatus.DONE);
                    row.setErrorMessage(null);
                    row.setProvider(provider.id());
                }
                repository.save(row);
            }
        } catch (Exception exception) {
            markFailed(rows, exception.getMessage());
        }
    }

    private void markFailed(List<ContentTranslation> rows, String message) {
        var text = message == null ? "failed" : message.substring(0, Math.min(message.length(), 500));
        for (var row : rows) {
            row.setErrorMessage(text);
            row.setStatus(row.getAttempts() >= properties.maxAttempts() ? TranslationStatus.FAILED : TranslationStatus.PENDING);
            repository.save(row);
        }
    }

    private boolean providerReady() {
        if (provider.configured() && !"none".equals(provider.id())) return true;
        if (missingProviderLogged.compareAndSet(false, true)) {
            log.warn("translation provider is not configured; pending jobs stay PENDING");
        }
        return false;
    }

    private boolean readyToRetry(ContentTranslation row) {
        if (row.getAttempts() <= 0 || properties.retryBackoffMs() <= 0) return true;
        var modified = row.getModifiedDateTime();
        if (modified == null) return true;
        var shift = Math.min(row.getAttempts() - 1, 5);
        var waitMs = Math.min(3_600_000L, (long) properties.retryBackoffMs() << shift);
        return !modified.plusMillis(waitMs).isAfter(Instant.now());
    }

    private String lookupThai(ContentTranslation row) {
        return thaiSources.thaiText(row.getEntityType(), row.getEntityId(), row.getFieldName());
    }

    public record BackfillResult(int queued, int skipped, boolean alreadyRunning) {}

    public interface BackfillSource {
        List<BackfillItem> items(TranslationEntityType entityType);

        record BackfillItem(String entityId, Map<String, String> fields) {}
    }
}
