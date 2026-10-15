package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderId(UUID orderId);
    List<Payment> findByOrderIdOrderByAttemptNoAsc(UUID orderId);
    Optional<Payment> findFirstByOrderIdOrderByAttemptNoDesc(UUID orderId);
    Optional<Payment> findFirstByOrderIdAndStatusOrderByAttemptNoDesc(UUID orderId, String status);
    boolean existsByProviderAndProviderPaymentId(String provider, String providerPaymentId);
    boolean existsByProviderPaymentIdIgnoreCaseAndShopIdAndOrderIdNot(String providerPaymentId, UUID shopId, UUID orderId);
}
