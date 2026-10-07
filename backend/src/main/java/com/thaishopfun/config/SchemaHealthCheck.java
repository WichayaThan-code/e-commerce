package com.thaishopfun.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class SchemaHealthCheck implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaHealthCheck.class);
    private final DataSource dataSource;
    private final Environment environment;

    public SchemaHealthCheck(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var url = environment.getProperty("spring.datasource.url", "");
        if (url.startsWith("jdbc:h2:")) {
            log.info("ข้ามการตรวจ schema เพราะใช้ฐานข้อมูลทดสอบ H2");
            return;
        }
        var required = new LinkedHashMap<String, List<String>>();
        required.put("users", List.of("id", "email", "permission_version"));
        required.put("refresh_tokens", List.of("id", "user_id"));
        required.put("merchant_applications", List.of("id", "user_id", "owner_id", "citizen_id_hash"));
        required.put("merchant_documents", List.of("id", "application_id"));
        required.put("shops", List.of("id", "shop_no", "shop_id", "owner_id", "opening_hours", "category_id"));
        required.put("shop_members", List.of("id", "shop_id", "user_id"));
        required.put("owners", List.of("id", "user_id", "citizen_id_hash"));
        required.put("owner_audit_log", List.of("id", "owner_id", "field_name"));
        required.put("audit_logs", List.of("id", "resource_type", "resource_id"));
        required.put("product_categories", List.of("category_id", "name_th"));
        required.put("hub_categories", List.of("id", "category_no", "name_th", "name_zh"));
        required.put("shop_reinstatement_requests", List.of("id", "request_id", "shop_id"));
        required.put("products", List.of("id", "shop_id", "slug", "name_th", "status", "currency", "created_date_time", "modified_by"));
        required.put("product_skus", List.of("id", "product_id", "shop_id", "sku_code", "price", "option_values"));
        required.put("product_images", List.of("id", "product_id", "storage_path", "is_primary"));
        required.put("inventory_levels", List.of("id", "shop_id", "sku_id", "on_hand", "available"));
        required.put("inventory_ledger", List.of("id", "shop_id", "sku_id", "entry_type", "idempotency_key"));
        required.put("content_translations", List.of("id", "entity_type", "entity_id", "field_name", "locale", "status", "created_date_time", "modified_date_time"));
        var missing = new ArrayList<String>();
        try (var connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(null, "public", "%", "%")) {
            var present = new java.util.HashSet<String>();
            while (columns.next()) {
                var table = columns.getString("TABLE_NAME");
                var column = columns.getString("COLUMN_NAME");
                if (table != null && column != null) present.add(table.toLowerCase() + "." + column.toLowerCase());
            }
            required.forEach((table, names) -> names.forEach(column -> {
                if (!present.contains(table + "." + column)) missing.add(table + "." + column);
            }));
        }
        if (missing.isEmpty()) log.info("schema ครบ");
        else log.error("schema ไม่ครบ: {}", String.join(", ", missing));
    }
}
