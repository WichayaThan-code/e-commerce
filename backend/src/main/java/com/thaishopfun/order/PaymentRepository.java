package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderId(UUID orderId);
    boolean existsByProviderAndProviderPaymentId(String provider, String providerPaymentId);
}
