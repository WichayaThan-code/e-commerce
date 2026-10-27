package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderAddressRepository extends JpaRepository<OrderAddress, UUID> {
    /** order_id + recipient_name only — batch for merchant order list customer column. */
    @Query("""
        select a.order.id, a.recipientName from OrderAddress a
         where a.order.id in :orderIds
         order by a.createdDateTime asc
        """)
    List<Object[]> recipientNamesByOrderIds(@Param("orderIds") Collection<UUID> orderIds);
}
