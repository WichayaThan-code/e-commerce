package com.thaishopfun.admin.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, UUID> {
    boolean existsByDedupeKey(String dedupeKey);

    Optional<AdminNotification> findByDedupeKey(String dedupeKey);

    /**
     * First page — no nullable cursor predicates.
     * (PostgreSQL cannot infer the JDBC type of a null Instant bound into {@code :cursorAt is null}.)
     */
    @Query("""
        select n from AdminNotification n
        where n.requiredPermission in :permissions
        order by n.createdAt desc, n.id desc
        """)
    List<AdminNotification> pageFirstForPermissions(
        @Param("permissions") Collection<String> permissions,
        Pageable pageable
    );

    /** Next page — keyset after a fully decoded non-null cursor. */
    @Query("""
        select n from AdminNotification n
        where n.requiredPermission in :permissions
          and (
            n.createdAt < :cursorAt
            or (n.createdAt = :cursorAt and n.id < :cursorId)
          )
        order by n.createdAt desc, n.id desc
        """)
    List<AdminNotification> pageAfterForPermissions(
        @Param("permissions") Collection<String> permissions,
        @Param("cursorAt") Instant cursorAt,
        @Param("cursorId") UUID cursorId,
        Pageable pageable
    );

    @Query("""
        select count(n) from AdminNotification n
        where n.requiredPermission in :permissions
          and not exists (
            select 1 from AdminNotificationRead r
            where r.notificationId = n.id and r.userId = :userId
          )
        """)
    long countUnreadForUser(
        @Param("userId") UUID userId,
        @Param("permissions") Collection<String> permissions
    );
}
