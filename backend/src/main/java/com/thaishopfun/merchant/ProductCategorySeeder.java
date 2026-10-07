package com.thaishopfun.merchant;

import com.thaishopfun.translation.ContentTranslationBridge;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductCategorySeeder implements ApplicationRunner {
    private final ProductCategoryRepository categories;
    private final ContentTranslationBridge translationBridge;

    public ProductCategorySeeder(ProductCategoryRepository categories, ContentTranslationBridge translationBridge) {
        this.categories = categories;
        this.translationBridge = translationBridge;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (categories.count() > 0) return;
        var seeded = categories.saveAll(List.of(
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
            row("เครื่องประดับและนาฬิกา", "Jewelry & watches", "珠宝手表", 13)
        ));
        seeded.forEach(translationBridge::onCategorySaved);
    }

    private static ProductCategory row(String nameTh, String nameEn, String nameZh, int sortOrder) {
        return new ProductCategory(nameTh, nameEn, nameZh, sortOrder);
    }
}
