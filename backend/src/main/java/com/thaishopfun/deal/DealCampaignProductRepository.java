package com.thaishopfun.deal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DealCampaignProductRepository extends JpaRepository<DealCampaignProduct, UUID> {
    List<DealCampaignProduct> findByCampaignIdOrderBySortOrderAscCreatedDateTimeAsc(UUID campaignId);

    Optional<DealCampaignProduct> findByCampaignIdAndProductId(UUID campaignId, UUID productId);

    long countByCampaignId(UUID campaignId);

    boolean existsByCampaignIdAndProductId(UUID campaignId, UUID productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DealCampaignProduct p where p.campaignId = :campaignId and p.productId = :productId")
    int deleteByCampaignIdAndProductId(@Param("campaignId") UUID campaignId, @Param("productId") UUID productId);

    @Query("""
        select p.productId from DealCampaignProduct p
         where p.campaignId in :campaignIds
         order by p.sortOrder asc, p.createdDateTime asc
        """)
    List<UUID> findProductIdsByCampaignIdIn(@Param("campaignIds") Collection<UUID> campaignIds);
}
