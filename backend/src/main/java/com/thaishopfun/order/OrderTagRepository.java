package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderTagRepository extends JpaRepository<OrderTag, UUID> {
    List<OrderTag> findByShopIdOrderByNameAsc(UUID shopId);

    boolean existsByShopIdAndNameIgnoreCase(UUID shopId, String name);

    @Query("select count(t) > 0 from OrderTag t where t.shopId = :shopId and lower(t.name) = lower(:name) and t.id <> :excludeId")
    boolean existsByShopIdAndNameIgnoreCaseExcluding(@Param("shopId") UUID shopId, @Param("name") String name, @Param("excludeId") UUID excludeId);

    long countByShopId(UUID shopId);

    Optional<OrderTag> findByIdAndShopId(UUID id, UUID shopId);

    List<OrderTag> findByShopIdAndIdIn(UUID shopId, Collection<UUID> ids);
}
