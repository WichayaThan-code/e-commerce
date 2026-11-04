package com.thaishopfun.content;

import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.content.ContentDtos.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Public articles: only PUBLISHED, non-deleted articles whose published_at has been reached. */
@PublicEndpoint("public articles")
@RestController
@RequestMapping("/api")
public class ContentPublicController {
    private final ContentService content;

    public ContentPublicController(ContentService content) {
        this.content = content;
    }

    @GetMapping("/articles")
    public PageResponse<ArticleCard> articles(@RequestParam(required = false) String lang,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) String tag,
                                              @RequestParam(required = false) String audience,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return content.listArticles(lang, category, q, tag, audience, page, size);
    }

    @GetMapping("/articles/featured")
    public List<ArticleCard> featured(@RequestParam(required = false) String lang) {
        return content.featured(lang);
    }

    @GetMapping("/articles/{slug}")
    public ArticleDetail article(@PathVariable String slug, @RequestParam(required = false) String lang) {
        return content.articleDetail(slug, lang);
    }

    @GetMapping("/article-categories")
    public List<CategoryView> categories(@RequestParam(required = false) String lang) {
        return content.listCategories(lang);
    }

    @GetMapping("/article-covers/{mediaId}")
    public ResponseEntity<Resource> cover(@PathVariable UUID mediaId) {
        return content.serveCover(mediaId);
    }
}
