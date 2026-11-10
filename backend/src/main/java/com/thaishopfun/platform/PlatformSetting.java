package com.thaishopfun.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "platform_settings")
public class PlatformSetting {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "setting_key", nullable = false, length = 100, unique = true)
    private String settingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "setting_value", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settingValue;

    @Column(name = "is_admin_editable", nullable = false)
    private boolean adminEditable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_schema", columnDefinition = "jsonb")
    private Map<String, Object> valueSchema;

    @Column(length = 500)
    private String description;

    @Column(name = "created_date_time", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "modified_date_time", nullable = false)
    private Instant updatedAt;

    @Column(name = "modified_by")
    private UUID updatedBy;

    protected PlatformSetting() {}

    public PlatformSetting(String settingKey, Map<String, Object> settingValue, boolean adminEditable, String description) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.adminEditable = adminEditable;
        this.description = description;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void replaceValue(Map<String, Object> value, UUID actorId) {
        this.settingValue = value;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public String getSettingKey() { return settingKey; }
    public Map<String, Object> getSettingValue() { return settingValue; }
    public boolean isAdminEditable() { return adminEditable; }
    public Map<String, Object> getValueSchema() { return valueSchema; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
}
