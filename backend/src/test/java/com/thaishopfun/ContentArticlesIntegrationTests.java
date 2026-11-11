package com.thaishopfun;

import com.jayway.jsonpath.JsonPath;
import com.thaishopfun.auth.OtpVerificationRepository;
import com.thaishopfun.auth.PasswordResetTokenRepository;
import com.thaishopfun.auth.RefreshTokenRepository;
import com.thaishopfun.auth.UserAccount;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.auth.authz.PlatformRole;
import com.thaishopfun.auth.authz.PlatformRoleRepository;
import com.thaishopfun.content.BlogCategory;
import com.thaishopfun.content.BlogCategoryRepository;
import com.thaishopfun.content.BlogPost;
import com.thaishopfun.content.BlogPostRepository;
import com.thaishopfun.order.MediaFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentArticlesIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired BlogPostRepository posts;
    @Autowired BlogCategoryRepository categories;
    @Autowired MediaFileRepository mediaFiles;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordResetTokenRepository passwordResetTokens;
    @Autowired OtpVerificationRepository otpVerifications;
    @Autowired UserAccountRepository users;
    @Autowired PlatformRoleRepository platformRoles;
    @Autowired PasswordEncoder passwords;
    @Autowired com.thaishopfun.merchant.ShopReinstatementRequestRepository reinstatements;
    @Autowired com.thaishopfun.merchant.ShopMemberRepository shopMembers;
    @Autowired com.thaishopfun.merchant.ShopRepository shops;
    @Autowired com.thaishopfun.merchant.MerchantDocumentRepository documents;
    @Autowired com.thaishopfun.merchant.MerchantApplicationRepository applications;
    @Autowired com.thaishopfun.audit.PlatformAuditLogRepository auditLogs;

    @BeforeEach void clean() {
        posts.deleteAll();
        categories.deleteAll();
        mediaFiles.deleteAll();
        platformRoles.deleteAll();
        reinstatements.deleteAll();
        shopMembers.deleteAll();
        shops.deleteAll();
        documents.deleteAll();
        applications.deleteAll();
        otpVerifications.deleteAll();
        passwordResetTokens.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test void publicEndpointsShowOnlyPublishedVisibleArticlesLocalizedAndFiltered() throws Exception {
        var tips = category("tips", 2, true, "เคล็ดลับ", "Tips", null);
        var news = category("news", 1, true, "ข่าว", "News", "新闻");
        category("hidden", 3, false, "ซ่อน", "Hidden", null);
        var now = Instant.now();
        article("published-old", tips, BlogPost.PUBLISHED, now.minus(3, ChronoUnit.DAYS), false, "บทความเก่า", "Old article", null, List.of("ขาย", "sale"));
        article("published-new", tips, BlogPost.PUBLISHED, now.minus(1, ChronoUnit.DAYS), true, "บทความใหม่", "New article", "新文章", List.of("shop"));
        article("in-news", news, BlogPost.PUBLISHED, now.minus(2, ChronoUnit.DAYS), true, "ข่าวด่วน", null, null, List.of());
        article("draft-one", tips, BlogPost.DRAFT, null, false, "ฉบับร่าง", "Draft", null, List.of());
        article("archived-one", tips, BlogPost.ARCHIVED, now.minus(5, ChronoUnit.DAYS), false, "เก็บถาวร", "Archived", null, List.of());
        article("future-one", tips, BlogPost.PUBLISHED, now.plus(2, ChronoUnit.DAYS), false, "อนาคต", "Future", null, List.of());
        var deleted = article("deleted-one", tips, BlogPost.PUBLISHED, now.minus(4, ChronoUnit.DAYS), false, "ลบแล้ว", "Deleted", null, List.of());
        deleted.setDeletedAt(now);
        posts.save(deleted);

        // only published + not deleted + already live, newest first (no params must be 200 on Postgres/H2)
        mvc.perform(get("/api/articles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].slug").value("published-new"))
            .andExpect(jsonPath("$.items[0].title").value("บทความใหม่"))
            .andExpect(jsonPath("$.items[0].category.slug").value("tips"))
            .andExpect(jsonPath("$.items[0].featured").value(true))
            .andExpect(jsonPath("$.items[0].tags[0]").value("shop"))
            .andExpect(jsonPath("$.items[1].slug").value("in-news"))
            .andExpect(jsonPath("$.items[2].slug").value("published-old"));
        // language with Thai fallback for blank translations
        mvc.perform(get("/api/articles").param("lang", "th"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.items[0].title").value("บทความใหม่"));
        mvc.perform(get("/api/articles?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title").value("New article"))
            .andExpect(jsonPath("$.items[1].title").value("ข่าวด่วน"))
            .andExpect(jsonPath("$.items[1].category.name").value("News"));
        mvc.perform(get("/api/articles?lang=zh"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title").value("新文章"))
            .andExpect(jsonPath("$.items[2].title").value("บทความเก่า"));
        // filters (all 200; never return draft/archived/future/deleted)
        mvc.perform(get("/api/articles").param("category", "news"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].slug").value("in-news"))
            .andExpect(jsonPath("$.items[0].status").doesNotExist());
        mvc.perform(get("/api/articles").param("tag", "sale"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].slug").value("published-old"));
        mvc.perform(get("/api/articles").param("tag", "nope"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/articles").param("q", "NEW ART"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/articles").param("q", "ข่าว"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].slug").value("in-news"));
        mvc.perform(get("/api/articles").param("q", "draft"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        // paging
        mvc.perform(get("/api/articles").param("page", "1").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items[0].slug").value("published-old"));
        // published-only: total stays 3 even when asking for a large page
        mvc.perform(get("/api/articles").param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.items.length()").value(3));

        mvc.perform(get("/api/articles/featured"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].slug").value("published-new"))
            .andExpect(jsonPath("$[1].slug").value("in-news"));

        // categories: active only, ordered, with published counts
        mvc.perform(get("/api/article-categories?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].slug").value("news"))
            .andExpect(jsonPath("$[0].name").value("News"))
            .andExpect(jsonPath("$[0].articleCount").value(1))
            .andExpect(jsonPath("$[1].slug").value("tips"))
            .andExpect(jsonPath("$[1].articleCount").value(2));
        mvc.perform(get("/api/article-categories?lang=zh"))
            .andExpect(jsonPath("$[0].name").value("新闻"))
            .andExpect(jsonPath("$[1].name").value("เคล็ดลับ"));
    }

    @Test void draftArchivedFutureAndDeletedArticlesAre404OnPublicDetail() throws Exception {
        var cat = category("tips", 1, true, "เคล็ดลับ", "Tips", null);
        var now = Instant.now();
        article("draft-one", cat, BlogPost.DRAFT, null, false, "ฉบับร่าง", "Draft", null, List.of());
        article("archived-one", cat, BlogPost.ARCHIVED, now.minus(1, ChronoUnit.DAYS), false, "เก็บถาวร", "Archived", null, List.of());
        article("future-one", cat, BlogPost.PUBLISHED, now.plus(1, ChronoUnit.DAYS), false, "อนาคต", "Future", null, List.of());
        var gone = article("deleted-one", cat, BlogPost.PUBLISHED, now.minus(1, ChronoUnit.DAYS), false, "ลบ", "Deleted", null, List.of());
        gone.setDeletedAt(now);
        posts.save(gone);
        for (String slug : new String[]{"draft-one", "archived-one", "future-one", "deleted-one", "does-not-exist"}) {
            mvc.perform(get("/api/articles/" + slug)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"));
        }
    }

    @Test void publicDetailIsLocalizedHasMetaAuthorDisplayNameOnlyAndRelatedArticles() throws Exception {
        var author = users.save(UserAccount.systemAccount("author@example.com", passwords.encode("Password1")));
        var cat = category("tips", 1, true, "เคล็ดลับ", "Tips", null);
        var other = category("news", 2, true, "ข่าว", "News", null);
        var now = Instant.now();
        var main = article("main", cat, BlogPost.PUBLISHED, now.minus(10, ChronoUnit.DAYS), false, "หลัก", "Main", null, List.of("a"));
        main.setBodyMd("เนื้อหาไทย");
        main.setBodyMdEn("English body");
        main.setMetaTitle("SEO title");
        main.setMetaDescription("SEO description");
        setAuthor(main, author.getId());
        posts.save(main);
        article("same-1", cat, BlogPost.PUBLISHED, now.minus(1, ChronoUnit.DAYS), false, "ร่วม 1", "Same 1", null, List.of());
        article("same-2", cat, BlogPost.PUBLISHED, now.minus(2, ChronoUnit.DAYS), false, "ร่วม 2", "Same 2", null, List.of());
        article("other-1", other, BlogPost.PUBLISHED, now.minus(3, ChronoUnit.DAYS), false, "อื่น 1", "Other 1", null, List.of());
        article("other-2", other, BlogPost.PUBLISHED, now.minus(4, ChronoUnit.DAYS), false, "อื่น 2", "Other 2", null, List.of());
        article("same-draft", cat, BlogPost.DRAFT, null, false, "ร่าง", "Draft", null, List.of());

        var body = mvc.perform(get("/api/articles/main?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Main"))
            .andExpect(jsonPath("$.bodyMd").value("English body"))
            .andExpect(jsonPath("$.metaTitle").value("SEO title"))
            .andExpect(jsonPath("$.metaDescription").value("SEO description"))
            .andExpect(jsonPath("$.author").value("System Administrator"))
            .andExpect(jsonPath("$.category.name").value("Tips"))
            .andExpect(jsonPath("$.related.length()").value(3))
            .andExpect(jsonPath("$.related[0].slug").value("same-1"))
            .andExpect(jsonPath("$.related[1].slug").value("same-2"))
            .andExpect(jsonPath("$.related[2].slug").value("other-1"))
            .andExpect(jsonPath("$.relatedProducts.length()").value(0))
            .andReturn().getResponse().getContentAsString();
        // never leaks contact data or the author id
        assertFalse(body.contains("author@example.com"));
        assertFalse(body.contains("0000000000"));
        assertFalse(body.contains(author.getId().toString()));
        // zh body is blank: Thai fallback
        mvc.perform(get("/api/articles/main?lang=zh")).andExpect(jsonPath("$.bodyMd").value("เนื้อหาไทย"));
    }

    @Test void adminEndpointsRejectAnonymousAndUsersWithoutContentPermissions() throws Exception {
        String customer = tokenFor("content-customer@example.com", "0812220001");
        var id = UUID.randomUUID();
        String body = articleJson("x-article", null, "หัวข้อ", "เนื้อหา");
        String catBody = "{\"slug\":\"x\",\"nameTh\":\"ก\"}";
        mvc.perform(get("/api/admin/articles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/article-categories")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/articles").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        var auth = "Bearer " + customer;
        mvc.perform(get("/api/admin/articles").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/articles/" + id).header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/article-categories").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/articles/" + id).header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/articles/" + id + "/status").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/articles/" + id + "/featured").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"featured\":true}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/articles/" + id + "/cover")
            .file(new MockMultipartFile("file", "c.png", "image/png", png(10))).header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/articles/" + id).header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/article-categories").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content(catBody)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/article-categories/reorder").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/article-categories/" + id).header("Authorization", auth)).andExpect(status().isForbidden());
        assertEquals(0, posts.count());
        assertEquals(0, categories.count());
    }

    @Test void platformAdminManagesArticlesFullLifecycle() throws Exception {
        String admin = adminToken();
        var auth = "Bearer " + admin;

        // ---- category first ----
        var catCreated = mvc.perform(post("/api/admin/article-categories").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"guides\",\"nameTh\":\"คู่มือ\",\"nameEn\":\"Guides\",\"descriptionTh\":\"รวมคู่มือ\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("guides"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.sortOrder").value(1))
            .andExpect(jsonPath("$.articleCount").value(0))
            .andReturn();
        String catId = JsonPath.read(catCreated.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/article-categories").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"guides\",\"nameTh\":\"ซ้ำ\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_CATEGORY_SLUG_TAKEN"));
        mvc.perform(post("/api/admin/article-categories").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"Bad Slug\",\"nameTh\":\"x\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTICLE_SLUG_INVALID"));
        var cat2 = mvc.perform(post("/api/admin/article-categories").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"promos\",\"nameTh\":\"โปรโมชั่น\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.sortOrder").value(2)).andReturn();
        String cat2Id = JsonPath.read(cat2.getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/admin/article-categories/reorder").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + cat2Id + "\",\"" + catId + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("promos")).andExpect(jsonPath("$[0].sortOrder").value(1))
            .andExpect(jsonPath("$[1].slug").value("guides"));
        mvc.perform(patch("/api/admin/article-categories/" + cat2Id + "/active").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/article-categories")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/article-categories").header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        // ---- create article (always DRAFT) ----
        String createBody = articleJson("welcome-guide", catId, "ยินดีต้อนรับ", "## เนื้อหา\n\nสวัสดี");
        var created = mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("welcome-guide"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.audience").value("ALL"))
            .andExpect(jsonPath("$.featured").value(false))
            .andExpect(jsonPath("$.categorySlug").value("guides"))
            .andExpect(jsonPath("$.tags[0]").value("เริ่มต้น"))
            .andExpect(jsonPath("$.tags.length()").value(2))
            .andExpect(jsonPath("$.readingMinutes").value(1))
            .andExpect(jsonPath("$.viewCount").value(0))
            .andExpect(jsonPath("$.authorName").value("System Administrator"))
            .andExpect(jsonPath("$.publishedAt").doesNotExist())
            .andReturn();
        String articleId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // duplicate slug -> 409, reserved slug -> 409, validation -> 400
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_SLUG_TAKEN"));
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("featured", null, "x", "y")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_SLUG_TAKEN"));
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("Upper_Case", null, "x", "y")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTICLE_SLUG_INVALID"));
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("no-title", null, " ", "y")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTICLE_FIELD_REQUIRED"));
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("no-body", null, "x", "")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTICLE_FIELD_REQUIRED"));
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("no-slug", null, "x", "y").replace("\"no-slug\"", "null")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("long-title", null, "ก".repeat(256), "y")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTICLE_FIELD_TOO_LONG"));
        mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("bad-cat", UUID.randomUUID().toString(), "x", "y")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTICLE_CATEGORY_INVALID"));
        assertEquals(1, posts.count());

        // draft is invisible publicly but visible to admin (all statuses)
        mvc.perform(get("/api/articles/welcome-guide")).andExpect(status().isNotFound());
        mvc.perform(get("/api/articles")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/articles").header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].status").value("DRAFT"));
        mvc.perform(get("/api/admin/articles?status=PUBLISHED").header("Authorization", auth))
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/articles?status=BOGUS").header("Authorization", auth))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTICLE_INVALID_STATUS"));

        // ---- update ----
        String updateBody = """
          {"slug":"welcome-guide","categoryId":"%s","audience":"merchant","featured":true,
           "titleTh":"ยินดีต้อนรับ v2","titleEn":"Welcome v2","excerptTh":"สรุป","bodyMdTh":"เนื้อหาใหม่","bodyMdEn":"New body",
           "tags":["a","b","a"," "],"metaTitle":"SEO","metaDescription":"Desc"}
          """.formatted(catId);
        mvc.perform(put("/api/admin/articles/" + articleId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titleTh").value("ยินดีต้อนรับ v2"))
            .andExpect(jsonPath("$.titleEn").value("Welcome v2"))
            .andExpect(jsonPath("$.audience").value("MERCHANT"))
            .andExpect(jsonPath("$.featured").value(true))
            .andExpect(jsonPath("$.readingMinutes").value(1))
            .andExpect(jsonPath("$.viewCount").value(0))
            .andExpect(jsonPath("$.tags.length()").value(2))
            .andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(put("/api/admin/articles/" + UUID.randomUUID()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isNotFound());
        // another article cannot take the slug
        var second = mvc.perform(post("/api/admin/articles").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("second-article", null, "สอง", "เนื้อหา")))
            .andExpect(status().isCreated()).andReturn();
        String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/admin/articles/" + secondId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(articleJson("welcome-guide", null, "สอง", "เนื้อหา")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_SLUG_TAKEN"));

        // ---- category in use cannot be deleted ----
        mvc.perform(delete("/api/admin/article-categories/" + catId).header("Authorization", auth))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_CATEGORY_IN_USE"));

        // ---- cover upload ----
        mvc.perform(multipart("/api/admin/articles/" + articleId + "/cover")
                .file(new MockMultipartFile("file", "notes.txt", "text/plain", "not an image".getBytes())).header("Authorization", auth))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/articles/" + articleId + "/cover")
                .file(new MockMultipartFile("file", "big.png", "image/png", png(5 * 1024 * 1024 + 1))).header("Authorization", auth))
            .andExpect(status().isPayloadTooLarge());
        var withCover = mvc.perform(multipart("/api/admin/articles/" + articleId + "/cover")
                .file(new MockMultipartFile("file", "cover.png", "image/png", png(2048))).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.coverUrl").isNotEmpty()).andReturn();
        String coverUrl = JsonPath.read(withCover.getResponse().getContentAsString(), "$.coverUrl");
        mvc.perform(get(coverUrl)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        assertEquals(1, mediaFiles.count());
        // replacing the cover removes the old media row
        var replaced = mvc.perform(multipart("/api/admin/articles/" + articleId + "/cover")
                .file(new MockMultipartFile("file", "cover2.png", "image/png", png(4096))).header("Authorization", auth))
            .andExpect(status().isOk()).andReturn();
        String newCoverUrl = JsonPath.read(replaced.getResponse().getContentAsString(), "$.coverUrl");
        assertFalse(coverUrl.equals(newCoverUrl));
        assertEquals(1, mediaFiles.count());
        mvc.perform(get(coverUrl)).andExpect(status().isNotFound());
        mvc.perform(get(newCoverUrl)).andExpect(status().isOk());

        // ---- publish: sets published_at once, becomes public ----
        var published = mvc.perform(patch("/api/admin/articles/" + articleId + "/status").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.publishedAt").isNotEmpty()).andReturn();
        String firstPublishedAt = JsonPath.read(published.getResponse().getContentAsString(), "$.publishedAt");
        mvc.perform(get("/api/articles/welcome-guide?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Welcome v2"))
            .andExpect(jsonPath("$.coverUrl").value(newCoverUrl))
            .andExpect(jsonPath("$.author").value("System Administrator"))
            .andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/articles/welcome-guide?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewCount").value(2));
        mvc.perform(get("/api/articles")).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].coverUrl").value(newCoverUrl));
        mvc.perform(get("/api/articles/featured")).andExpect(jsonPath("$.length()").value(1));
        // unpublish -> archive -> republish keeps the original published_at
        mvc.perform(patch("/api/admin/articles/" + articleId + "/status").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/articles/welcome-guide")).andExpect(status().isNotFound());
        var republished = mvc.perform(patch("/api/admin/articles/" + articleId + "/status").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"published\"}"))
            .andExpect(status().isOk()).andReturn();
        assertEquals(firstPublishedAt, JsonPath.read(republished.getResponse().getContentAsString(), "$.publishedAt"));
        mvc.perform(patch("/api/admin/articles/" + articleId + "/status").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LIVE\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTICLE_INVALID_STATUS"));
        mvc.perform(patch("/api/admin/articles/" + articleId + "/status").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        // scheduled publish stays hidden until its time
        mvc.perform(patch("/api/admin/articles/" + secondId + "/status").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\",\"publishedAt\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/articles/second-article")).andExpect(status().isNotFound());

        // ---- featured toggle ----
        mvc.perform(patch("/api/admin/articles/" + articleId + "/featured").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"featured\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.featured").value(false));
        mvc.perform(patch("/api/admin/articles/" + articleId + "/featured").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles/featured")).andExpect(jsonPath("$.length()").value(0));

        // ---- cover removal ----
        mvc.perform(delete("/api/admin/articles/" + articleId + "/cover").header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.coverUrl").doesNotExist());
        assertEquals(0, mediaFiles.count());

        // ---- admin read of one, soft delete ----
        mvc.perform(get("/api/admin/articles/" + articleId).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.bodyMdEn").value("New body"));
        mvc.perform(delete("/api/admin/articles/" + articleId).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/articles/" + articleId).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/articles/welcome-guide")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/articles/" + articleId).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/articles").header("Authorization", auth)).andExpect(jsonPath("$.totalElements").value(1));
        assertEquals(2, posts.count(), "soft delete keeps the row");
        assertTrue(posts.findById(UUID.fromString(articleId)).orElseThrow().getDeletedAt() != null);

        // ---- category delete: soft-deleted + remaining article keep it in use until the last one is gone ----
        mvc.perform(delete("/api/admin/articles/" + secondId).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/article-categories/" + catId).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/article-categories/" + catId).header("Authorization", auth)).andExpect(status().isNotFound());

        // ---- audited with the manage permission ----
        var audits = auditLogs.findAll().stream().filter(log -> log.getAction().startsWith("ARTICLE_")).toList();
        assertFalse(audits.isEmpty());
        assertTrue(audits.stream().allMatch(log -> "PLATFORM_CONTENT_MANAGE".equals(log.getPermission())));
        assertTrue(audits.stream().anyMatch(log -> "ARTICLE_CREATE".equals(log.getAction()) && "ARTICLE".equals(log.getResourceType())));
        assertTrue(audits.stream().anyMatch(log -> "ARTICLE_STATUS_PUBLISHED".equals(log.getAction())));
        assertTrue(audits.stream().anyMatch(log -> "ARTICLE_CATEGORY_DELETE".equals(log.getAction()) && "ARTICLE_CATEGORY".equals(log.getResourceType())));
        assertTrue(audits.stream().anyMatch(log -> "ARTICLE_DELETE".equals(log.getAction())));
    }

    // ---------------- helpers ----------------

    private static String articleJson(String slug, String categoryId, String titleTh, String bodyTh) {
        return """
          {"slug":"%s","categoryId":%s,"titleTh":"%s","bodyMdTh":"%s","tags":["เริ่มต้น","tips"]}
          """.formatted(slug, categoryId == null ? "null" : "\"" + categoryId + "\"", titleTh,
            bodyTh.replace("\n", "\\n"));
    }

    /** PNG signature followed by filler: content sniffing only needs the 8 magic bytes. */
    private static byte[] png(int size) {
        var bytes = new byte[size];
        byte[] magic = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(magic, 0, bytes, 0, Math.min(magic.length, size));
        for (int i = magic.length; i < size; i++) bytes[i] = (byte) (i % 251);
        return bytes;
    }

    private BlogCategory category(String slug, int sort, boolean active, String th, String en, String zh) {
        var category = new BlogCategory(null);
        category.setSlug(slug);
        category.setSortOrder(sort);
        category.setActive(active);
        category.setNameTh(th);
        category.setNameEn(en);
        category.setNameZh(zh);
        return categories.save(category);
    }

    private BlogPost article(String slug, BlogCategory category, String status, Instant publishedAt, boolean featured,
                          String titleTh, String titleEn, String titleZh, List<String> tags) {
        var post = new BlogPost(null);
        post.setSlug(slug);
        post.setCategory(category);
        post.setStatus(status);
        post.setPublishedAt(publishedAt);
        post.setFeatured(featured);
        post.setTitle(titleTh);
        post.setTitleEn(titleEn);
        post.setTitleZh(titleZh);
        post.setBodyMd("เนื้อหา " + titleTh);
        post.setTags(tags);
        return posts.save(post);
    }

    private void setAuthor(BlogPost post, UUID authorId) throws Exception {
        var field = BlogPost.class.getDeclaredField("authorId");
        field.setAccessible(true);
        field.set(post, authorId);
    }

    private String adminToken() throws Exception {
        var admin = users.save(UserAccount.systemAccount("content-admin@example.com", passwords.encode("Password1")));
        platformRoles.save(PlatformRole.grant(admin.getId(), PlatformRole.PLATFORM_ADMIN, null, "test"));
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
          {"identity":"content-admin@example.com","password":"Password1"}
          """)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private String tokenFor(String email, String phone) throws Exception {
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
          {"firstName":"สมหญิง","lastName":"ใจดี","email":"%s","phone":"%s","password":"Password1"}
          """.formatted(email, phone))).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken");
    }
}
