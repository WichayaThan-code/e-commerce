package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, UUID> {
    List<PaymentProof> findByOrderIdOrderByCreatedDateTimeAsc(UUID orderId);
    List<PaymentProof> findByPaymentIdAndStatus(UUID paymentId, String status);

    /** Live (pending / verified) slips of the shop carrying this bank reference (case-insensitive). */
    @Query("select p from PaymentProof p where p.shopId = :shop and upper(p.referenceNo) = upper(:ref) and p.status in ('PENDING_REVIEW', 'VERIFIED')")
    List<PaymentProof> findLiveByReference(@Param("shop") UUID shopId, @Param("ref") String reference);
}
