package com.thaishopfun.catalog.configurator;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** V42 {@code product_configurators}: one (validated, normalized) configurator schema per product. */
@Entity
@Table(name = "product_configurators")
public class ProductConfigurator extends AuditedEntity {
    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema", nullable = false)
    private Map<String, Object> schema;

    @Column(nullable = false)
    private int version;

    protected ProductConfigurator() {}

    public ProductConfigurator(UUID productId) {
        this.productId = productId;
        this.schema = new LinkedHashMap<>();
        this.version = 0;
    }

    public void apply(boolean enabled, Map<String, Object> schema, UUID actorId) {
        this.enabled = enabled;
        this.schema = schema;
        this.version = version + 1;
        stamp(actorId);
    }

    public UUID getProductId() { return productId; }
    public boolean isEnabled() { return enabled; }
    public Map<String, Object> getSchema() { return schema; }
    public int getVersion() { return version; }
}
