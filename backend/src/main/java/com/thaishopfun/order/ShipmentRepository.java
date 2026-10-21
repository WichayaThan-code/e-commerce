package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {
    Optional<Shipment> findFirstByOrderIdOrderByCreatedDateTimeDesc(UUID orderId);

    /** Live shipments (not CANCELLED) holding this tracking number for the carrier, case-insensitive. */
    @Query("select s from Shipment s where s.carrierCode = :carrier and upper(s.trackingNumber) = :tracking and s.status <> 'CANCELLED'")
    List<Shipment> findLiveByTracking(@Param("carrier") String carrier, @Param("tracking") String upperTracking);
}
