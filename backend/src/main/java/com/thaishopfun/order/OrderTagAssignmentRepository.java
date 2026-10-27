package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderTagAssignmentRepository extends JpaRepository<OrderTagAssignment, OrderTagAssignmentId> {
    List<OrderTagAssignment> findByOrderId(UUID orderId);

    List<OrderTagAssignment> findByOrderIdIn(Collection<UUID> orderIds);

    void deleteByOrderId(UUID orderId);

    long countByTagId(UUID tagId);

    @Query("select a.tagId, count(a) from OrderTagAssignment a where a.tagId in :tagIds group by a.tagId")
    List<Object[]> countByTagIdIn(@Param("tagIds") Collection<UUID> tagIds);

    @Query("select a.orderId from OrderTagAssignment a where a.tagId = :tagId")
    List<UUID> findOrderIdsByTagId(@Param("tagId") UUID tagId);
}
