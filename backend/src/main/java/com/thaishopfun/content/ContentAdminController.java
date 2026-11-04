package com.thaishopfun.content;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.content.ContentDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Platform admin management of articles and article categories. */
@RestController
@RequestMapping("/api/admin")
public class ContentAdminController {
    private final ContentService content;

    public ContentAdminController(ContentService content) {
        this.content = content;
    }

    // ---- articles ----
    @RequirePermission("PLATFORM_CONTENT_READ")
    @GetMapping("/articles")
    public PageResponse<AdminArticleSummary> articles(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        return content.adminArticles(status, category, q, page, size);
    }

    @RequirePermission("PLATFORM_CONTENT_READ")
    @GetMapping("/articles/{id}")
    public AdminArticle article(@PathVariable UUID id) { return content.adminArticle(id); }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PostMapping("/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminArticle createArticle(@RequestBody ArticleRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.createArticle(request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PutMapping("/articles/{id}")
    public AdminArticle updateArticle(@PathVariable UUID id, @RequestBody ArticleRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.updateArticle(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PatchMapping("/articles/{id}/status")
    public AdminArticle setStatus(@PathVariable UUID id, @RequestBody StatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.setStatus(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PatchMapping("/articles/{id}/featured")
    public AdminArticle setFeatured(@PathVariable UUID id, @RequestBody FeaturedRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.setFeatured(id, request == null ? null : request.featured(), RequestUser.id(jwt));
    }

    /** Cover image: JPG / PNG / WEBP up to 5 MB, content-sniffed; stored as a PUBLIC media file (purpose BLOG). */
    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PostMapping("/articles/{id}/cover")
    public AdminArticle uploadCover(@PathVariable UUID id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        return content.uploadCover(id, file, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @DeleteMapping("/articles/{id}/cover")
    public AdminArticle removeCover(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return content.removeCover(id, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @DeleteMapping("/articles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArticle(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        content.deleteArticle(id, RequestUser.id(jwt));
    }

    // ---- categories ----
    @RequirePermission("PLATFORM_CONTENT_READ")
    @GetMapping("/article-categories")
    public List<AdminCategory> categories() { return content.adminCategories(); }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PostMapping("/article-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCategory createCategory(@RequestBody CategoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.createCategory(request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PutMapping("/article-categories/reorder")
    public List<AdminCategory> reorderCategories(@RequestBody ReorderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.reorderCategories(request == null ? null : request.ids(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PutMapping("/article-categories/{id}")
    public AdminCategory updateCategory(@PathVariable UUID id, @RequestBody CategoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.updateCategory(id, request, RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @PatchMapping("/article-categories/{id}/active")
    public AdminCategory setCategoryActive(@PathVariable UUID id, @RequestBody ActiveRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.setCategoryActive(id, request == null ? null : request.active(), RequestUser.id(jwt));
    }

    @RequirePermission("PLATFORM_CONTENT_MANAGE")
    @DeleteMapping("/article-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        content.deleteCategory(id, RequestUser.id(jwt));
    }
}
