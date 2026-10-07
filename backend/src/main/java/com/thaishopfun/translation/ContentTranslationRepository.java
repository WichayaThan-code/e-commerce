package com.thaishopfun.translation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContentTranslationRepository extends JpaRepository<ContentTranslation, Long> {
    Optional<ContentTranslation> findByEntityTypeAndEntityIdAndFieldNameAndLocale(
        TranslationEntityType entityType,
        String entityId,
        String fieldName,
        String locale
    );

    List<ContentTranslation> findByEntityTypeAndEntityIdIn(
        TranslationEntityType entityType,
        Collection<String> entityIds
    );

    @Query("""
        select t from ContentTranslation t
        where t.status in :statuses
          and t.origin = com.thaishopfun.translation.TranslationOrigin.MACHINE
          and t.attempts < :maxAttempts
        order by t.modifiedDateTime asc
        """)
    List<ContentTranslation> findWorkQueue(
        @Param("statuses") Collection<TranslationStatus> statuses,
        @Param("maxAttempts") int maxAttempts
    );

    @Query("""
        select t from ContentTranslation t
        where t.entityType = :entityType
          and t.entityId = :entityId
          and t.status in :statuses
          and t.origin = com.thaishopfun.translation.TranslationOrigin.MACHINE
          and t.attempts < :maxAttempts
        """)
    List<ContentTranslation> findWorkQueueForEntity(
        @Param("entityType") TranslationEntityType entityType,
        @Param("entityId") String entityId,
        @Param("statuses") Collection<TranslationStatus> statuses,
        @Param("maxAttempts") int maxAttempts
    );
}
