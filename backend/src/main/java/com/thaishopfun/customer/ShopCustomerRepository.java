package com.thaishopfun.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopCustomerRepository extends JpaRepository<ShopCustomer, UUID> {
    Optional<ShopCustomer> findByIdAndShopIdAndDeletedAtIsNull(UUID id, UUID shopId);

    List<ShopCustomer> findByIdInAndShopIdAndDeletedAtIsNull(Collection<UUID> ids, UUID shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShopCustomer c where c.id = :id and c.shopId = :shopId and c.deletedAt is null")
    Optional<ShopCustomer> lockInShop(@Param("id") UUID id, @Param("shopId") UUID shopId);

    /** Live (not MERGED, not soft-deleted) customers of the shop sharing the normalized phone or email. */
    @Query("""
        select c from ShopCustomer c
        where c.shopId = :shopId and c.deletedAt is null and c.status <> com.thaishopfun.customer.CustomerStatus.MERGED
          and ((:phone is not null and c.phoneNormalized = :phone) or (:email is not null and c.emailNormalized = :email))
        order by c.createdDateTime asc
        """)
    List<ShopCustomer> findMatches(@Param("shopId") UUID shopId, @Param("phone") String phone, @Param("email") String email);

    boolean existsByShopIdAndCustomerCodeIgnoreCaseAndIdNot(UUID shopId, String customerCode, UUID id);

    List<ShopCustomer> findByMergedIntoId(UUID mergedIntoId);
}
