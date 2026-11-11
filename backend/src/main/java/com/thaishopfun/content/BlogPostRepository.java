package com.thaishopfun.content;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {
    Optional<BlogPost> findBySlug(String slug);
    Optional<BlogPost> findByIdAndDeletedAtIsNull(UUID id);
    long countByCategory_IdAndDeletedAtIsNull(UUID categoryId);

    /** Detaches (soft-deleted) articles from a category about to be removed; mirrors the V1 FK's ON DELETE SET NULL. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BlogPost p set p.category = null where p.category.id = :categoryId")
    int detachCategory(@Param("categoryId") UUID categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BlogPost p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int incrementViewCount(@Param("id") UUID id);

    /**
     * Public list (PUBLISHED, not deleted, published_at reached). Native SQL so PostgreSQL text[] tag filter works
     * ({@code = any(tags)}); Hibernate's JPQL {@code array_contains} compiles to {@code text[] @> varchar[]} which
     * Postgres rejects. Blank filters are empty strings (never null). No JOIN FETCH — categories load lazily in the
     * service transaction. Sort is fixed in SQL; pass an unsorted {@link Pageable}.
     */
    @Query(value = """
        select p.* from blog_posts p
        left join blog_categories c on c.id = p.category_id
         where p.status = 'PUBLISHED' and p.deleted_at is null
           and p.published_at is not null and p.published_at <= :now
           and (cast(:category as text) = '' or c.slug = cast(:category as text))
           and (cast(:audience as text) = '' or p.audience = cast(:audience as text) or p.audience = 'ALL')
           and (cast(:tag as text) = '' or cast(:tag as text) = any(p.tags))
           and (cast(:q as text) = ''
                or lower(p.title) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.title_en) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.title_zh) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.excerpt) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.excerpt_en) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.excerpt_zh) like lower(concat('%', cast(:q as text), '%')))
         order by p.published_at desc, p.id asc
        """,
        countQuery = """
        select count(*) from blog_posts p
        left join blog_categories c on c.id = p.category_id
         where p.status = 'PUBLISHED' and p.deleted_at is null
           and p.published_at is not null and p.published_at <= :now
           and (cast(:category as text) = '' or c.slug = cast(:category as text))
           and (cast(:audience as text) = '' or p.audience = cast(:audience as text) or p.audience = 'ALL')
           and (cast(:tag as text) = '' or cast(:tag as text) = any(p.tags))
           and (cast(:q as text) = ''
                or lower(p.title) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.title_en) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.title_zh) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.excerpt) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.excerpt_en) like lower(concat('%', cast(:q as text), '%'))
                or lower(p.excerpt_zh) like lower(concat('%', cast(:q as text), '%')))
        """,
        nativeQuery = true)
    Page<BlogPost> searchPublic(@Param("now") Instant now, @Param("category") String category,
                                @Param("audience") String audience, @Param("tag") String tag,
                                @Param("q") String q, Pageable pageable);

    @Query("""
        select p from BlogPost p left join fetch p.category
         where p.status = 'PUBLISHED' and p.deletedAt is null and p.publishedAt is not null and p.publishedAt <= :now
           and p.featured = true
         order by p.publishedAt desc, p.id asc
        """)
    List<BlogPost> findFeatured(@Param("now") Instant now, Pageable pageable);

    @Query("""
        select p from BlogPost p left join fetch p.category
         where p.slug = :slug and p.status = 'PUBLISHED' and p.deletedAt is null
           and p.publishedAt is not null and p.publishedAt <= :now
        """)
    Optional<BlogPost> findPublicBySlug(@Param("slug") String slug, @Param("now") Instant now);

    @Query("""
        select p from BlogPost p left join fetch p.category c
         where p.status = 'PUBLISHED' and p.deletedAt is null and p.publishedAt is not null and p.publishedAt <= :now
           and p.id <> :excludeId and c.id = :categoryId
         order by p.publishedAt desc, p.id asc
        """)
    List<BlogPost> findRelatedInCategory(@Param("now") Instant now, @Param("excludeId") UUID excludeId,
                                         @Param("categoryId") UUID categoryId, Pageable pageable);

    @Query("""
        select p from BlogPost p left join fetch p.category
         where p.status = 'PUBLISHED' and p.deletedAt is null and p.publishedAt is not null and p.publishedAt <= :now
           and p.id <> :excludeId
         order by p.publishedAt desc, p.id asc
        """)
    List<BlogPost> findRecent(@Param("now") Instant now, @Param("excludeId") UUID excludeId, Pageable pageable);

    @Query("""
        select count(p) from BlogPost p
         where p.category.id = :categoryId and p.status = 'PUBLISHED' and p.deletedAt is null
           and p.publishedAt is not null and p.publishedAt <= :now
        """)
    long countPublishedInCategory(@Param("categoryId") UUID categoryId, @Param("now") Instant now);

    /**
     * Admin list: every status except soft-deleted. Plain left join (no fetch) so pagination stays correct;
     * category is loaded lazily while mapping the page. Blank filters are empty strings.
     */
    @Query(value = """
        select p from BlogPost p left join p.category c
         where p.deletedAt is null
           and (:status = '' or p.status = :status)
           and (:category = '' or c.slug = :category)
           and (:q = ''
                or lower(p.title) like lower(concat('%', :q, '%'))
                or lower(p.titleEn) like lower(concat('%', :q, '%'))
                or lower(p.titleZh) like lower(concat('%', :q, '%'))
                or lower(p.slug) like lower(concat('%', :q, '%')))
         order by p.createdDateTime desc, p.id asc
        """,
        countQuery = """
        select count(p) from BlogPost p left join p.category c
         where p.deletedAt is null
           and (:status = '' or p.status = :status)
           and (:category = '' or c.slug = :category)
           and (:q = ''
                or lower(p.title) like lower(concat('%', :q, '%'))
                or lower(p.titleEn) like lower(concat('%', :q, '%'))
                or lower(p.titleZh) like lower(concat('%', :q, '%'))
                or lower(p.slug) like lower(concat('%', :q, '%')))
        """)
    Page<BlogPost> searchAdmin(@Param("status") String status, @Param("category") String category,
                               @Param("q") String q, Pageable pageable);
}
