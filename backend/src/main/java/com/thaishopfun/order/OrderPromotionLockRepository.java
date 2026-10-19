package com.thaishopfun.order;

import com.thaishopfun.cart.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Locks promotions used at checkout so usage limits cannot be exceeded by concurrent checkouts. */
public interface OrderPromotionLockRepository extends JpaRepository<Promotion, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id in :ids order by p.id")
    List<Promotion> lockAll(@Param("ids") Collection<UUID> ids);
}
