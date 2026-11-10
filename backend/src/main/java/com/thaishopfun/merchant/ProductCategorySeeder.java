package com.thaishopfun.merchant;

import com.thaishopfun.translation.ContentTranslationBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

/**
 * Ensures the standard marketplace categories exist (hub_categories). Runs on every startup and inserts only the
 * slugs that are missing, so a fresh database (where Flyway V38 already inserted OTOP) still gets the full set.
 * Existing rows are never updated, deleted or renumbered; category_no comes from the table's identity sequence.
 */
@Component
public class ProductCategorySeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProductCategorySeeder.class);

    /** Marketplace OTOP category (also inserted by Flyway V38); looked up by slug, never by id. */
    public static final String OTOP_SLUG = "otop-products";

    private final ProductCategoryRepository categories;
    private final ContentTranslationBridge translationBridge;

    public ProductCategorySeeder(ProductCategoryRepository categories, ContentTranslationBridge translationBridge) {
        this.categories = categories;
        this.translationBridge = translationBridge;
    }

    @Override
    public void run(ApplicationArguments args) {
        var existing = new HashSet<String>();
        categories.findAll().forEach(c -> existing.add(c.getSlug()));
        for (var category : standardCategories()) {
            if (existing.contains(category.getSlug())) continue;
            try {
                translationBridge.onCategorySaved(categories.saveAndFlush(category));
                log.info("hub_categories: added missing category {}", category.getSlug());
            } catch (DataIntegrityViolationException exception) {
                // another instance inserted it concurrently (unique slug) - nothing to do
                log.warn("hub_categories: skipped {} ({})", category.getSlug(), exception.getMostSpecificCause().getMessage());
            }
        }
    }

    /** Same 14 rows as local TSF_DB / seed_reference_data.sql section 4, in category_no order. */
    private static List<ProductCategory> standardCategories() {
        return List.of(
            row("แฟชั่นและเสื้อผ้า", "Fashion & apparel", "时尚服装", 1),
            row("อาหารและเครื่องดื่ม", "Food & beverages", "食品饮料", 2),
            row("ความงามและสุขภาพ", "Beauty & wellness", "美容健康", 3),
            row("บ้านและไลฟ์สไตล์", "Home & lifestyle", "家居生活", 4),
            row("สินค้าและงานฝีมือไทย", "Thai crafts & goods", "泰国手工艺", 5),
            row("อิเล็กทรอนิกส์", "Electronics", "电子产品", 6),
            row("อุปกรณ์กีฬา", "Sports equipment", "体育用品", 7),
            row("แม่และเด็ก", "Mom & baby", "母婴用品", 8),
            row("ยานยนต์และอะไหล่", "Automotive parts", "汽车配件", 9),
            row("สัตว์เลี้ยง", "Pets", "宠物用品", 10),
            row("เครื่องใช้ไฟฟ้า", "Home appliances", "家用电器", 11),
            row("หนังสือและเครื่องเขียน", "Books & stationery", "图书文具", 12),
            row("เครื่องประดับและนาฬิกา", "Jewelry & watches", "珠宝手表", 13),
            row(OTOP_SLUG, "สินค้า OTOP", "OTOP Products", "OTOP 一村一品", 14)
        );
    }

    private static ProductCategory row(String nameTh, String nameEn, String nameZh, int sortOrder) {
        var slug = nameEn.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return row(slug, nameTh, nameEn, nameZh, sortOrder);
    }

    private static ProductCategory row(String slug, String nameTh, String nameEn, String nameZh, int sortOrder) {
        return new ProductCategory(slug, nameTh, nameEn, nameZh, sortOrder);
    }
}
