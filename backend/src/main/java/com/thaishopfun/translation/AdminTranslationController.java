package com.thaishopfun.translation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/translations")
public class AdminTranslationController {
    private final TranslationService translations;
    private final TranslationBackfillService backfillSource;

    public AdminTranslationController(TranslationService translations, TranslationBackfillService backfillSource) {
        this.translations = translations;
        this.backfillSource = backfillSource;
    }

    @PostMapping("/backfill")
    public TranslationService.BackfillResult backfill(@RequestParam String entityType) {
        var type = TranslationEntityType.parse(entityType);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityType must be PRODUCT, PRODUCT_CATEGORY, or SHOP");
        }
        return translations.backfill(type, backfillSource);
    }
}
