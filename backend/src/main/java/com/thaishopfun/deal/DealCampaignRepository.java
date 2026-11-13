package com.thaishopfun.deal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DealCampaignRepository extends JpaRepository<DealCampaign, UUID> {
    List<DealCampaign> findAllByOrderBySortOrderAscCreatedDateTimeDesc();

    List<DealCampaign> findByKindOrderBySortOrderAscCreatedDateTimeDesc(DealCampaignKind kind);

    @Query("""
        select c from DealCampaign c
         where c.kind = :kind
           and c.active = true
           and c.startsAt <= :now
           and (c.endsAt is null or c.endsAt > :now)
         order by c.sortOrder asc, c.createdDateTime desc
        """)
    List<DealCampaign> findLiveByKind(@Param("kind") DealCampaignKind kind, @Param("now") Instant now);

    default Optional<DealCampaign> findPrimaryLive(DealCampaignKind kind, Instant now) {
        var rows = findLiveByKind(kind, now);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
