package com.thaishopfun.translation;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "content_translations",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_content_translations_entity",
        columnNames = {"entity_type", "entity_id", "field_name", "locale"}
    )
)
public class ContentTranslation extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private TranslationEntityType entityType;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    @Column(name = "translated_text")
    private String translatedText;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private TranslationOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TranslationStatus status;

    @Column(name = "provider", length = 30)
    private String provider;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    protected ContentTranslation() {}

    public ContentTranslation(
        TranslationEntityType entityType,
        String entityId,
        String fieldName,
        String locale,
        TranslationOrigin origin,
        TranslationStatus status
    ) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.fieldName = fieldName;
        this.locale = locale;
        this.origin = origin;
        this.status = status;
        this.attempts = 0;
    }

    public Long getId() { return id; }
    public TranslationEntityType getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getFieldName() { return fieldName; }
    public String getLocale() { return locale; }
    public String getTranslatedText() { return translatedText; }
    public void setTranslatedText(String translatedText) { this.translatedText = translatedText; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public TranslationOrigin getOrigin() { return origin; }
    public void setOrigin(TranslationOrigin origin) { this.origin = origin; }
    public TranslationStatus getStatus() { return status; }
    public void setStatus(TranslationStatus status) { this.status = status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
