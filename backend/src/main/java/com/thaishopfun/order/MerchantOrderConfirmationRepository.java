package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantOrderConfirmationRepository extends JpaRepository<MerchantOrderConfirmation, UUID> {
    List<MerchantOrderConfirmation> findByOrderIdOrderByConfirmedAtAsc(UUID orderId);
}
