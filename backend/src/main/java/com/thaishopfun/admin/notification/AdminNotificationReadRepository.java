package com.thaishopfun.admin.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AdminNotificationReadRepository extends JpaRepository<AdminNotificationRead, AdminNotificationRead.Pk> {
    boolean existsByNotificationIdAndUserId(UUID notificationId, UUID userId);

    @Query("""
        select r.notificationId from AdminNotificationRead r
        where r.userId = :userId and r.notificationId in :ids
        """)
    List<UUID> findReadIds(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    @Query("""
        select n.id from AdminNotification n
        where n.requiredPermission in :permissions
          and not exists (
            select 1 from AdminNotificationRead r
            where r.notificationId = n.id and r.userId = :userId
          )
        """)
    List<UUID> findUnreadIds(
        @Param("userId") UUID userId,
        @Param("permissions") Collection<String> permissions
    );
}
