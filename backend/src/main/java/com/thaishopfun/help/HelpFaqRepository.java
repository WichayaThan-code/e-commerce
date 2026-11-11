package com.thaishopfun.help;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelpFaqRepository extends JpaRepository<HelpFaq, UUID> {
    long countByTopic_Id(UUID topicId);

    long countByTopic_IdAndActiveTrue(UUID topicId);

    @Query("select coalesce(max(f.sortOrder), 0) from HelpFaq f")
    int maxSortOrder();

    @Query("select f from HelpFaq f join fetch f.topic t order by f.sortOrder asc, f.createdAt asc")
    List<HelpFaq> findAllWithTopic();

    /**
     * Public search. Blank filters are passed as empty strings (not null) so the same JPQL works on H2 and Postgres:
     * empty topic = any topic, empty audience = any audience, empty q = no text filter.
     */
    @Query("""
        select f from HelpFaq f join fetch f.topic t
         where f.active = true and t.active = true
           and (:topic = '' or t.slug = :topic)
           and (:audience = '' or f.audience = :audience or f.audience = 'ALL')
           and (:q = ''
                or lower(f.questionTh) like lower(concat('%', :q, '%'))
                or lower(f.questionEn) like lower(concat('%', :q, '%'))
                or lower(f.questionZh) like lower(concat('%', :q, '%'))
                or lower(f.answerTh) like lower(concat('%', :q, '%'))
                or lower(f.answerEn) like lower(concat('%', :q, '%'))
                or lower(f.answerZh) like lower(concat('%', :q, '%')))
         order by f.sortOrder asc, f.createdAt asc
        """)
    List<HelpFaq> search(@Param("topic") String topic, @Param("audience") String audience,
                         @Param("q") String q, Pageable pageable);

    @Query("select f from HelpFaq f join fetch f.topic t where f.id = :id and f.active = true and t.active = true")
    Optional<HelpFaq> findPublicById(@Param("id") UUID id);

    /** Atomic counter bumps (no lost updates under concurrent votes). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update HelpFaq f set f.helpfulYes = f.helpfulYes + 1 where f.id = :id")
    int incrementHelpfulYes(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update HelpFaq f set f.helpfulNo = f.helpfulNo + 1 where f.id = :id")
    int incrementHelpfulNo(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update HelpFaq f set f.helpfulYes = f.helpfulYes - 1 where f.id = :id and f.helpfulYes > 0")
    int decrementHelpfulYes(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update HelpFaq f set f.helpfulNo = f.helpfulNo - 1 where f.id = :id and f.helpfulNo > 0")
    int decrementHelpfulNo(@Param("id") UUID id);
}
