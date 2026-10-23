package com.thaishopfun.buyer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuyerAddressRepository extends JpaRepository<BuyerAddress, UUID> {
    List<BuyerAddress> findByBuyerUserIdAndDeletedAtIsNullOrderByDefaultAddressDescModifiedDateTimeDescIdAsc(UUID buyerUserId);

    Optional<BuyerAddress> findByIdAndBuyerUserIdAndDeletedAtIsNull(UUID id, UUID buyerUserId);

    long countByBuyerUserIdAndDeletedAtIsNull(UUID buyerUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BuyerAddress a set a.defaultAddress = false where a.buyerUserId = :buyerUserId and a.deletedAt is null and a.defaultAddress = true")
    int clearDefaults(@Param("buyerUserId") UUID buyerUserId);
}
