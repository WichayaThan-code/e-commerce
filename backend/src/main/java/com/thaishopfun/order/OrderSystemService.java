package com.thaishopfun.order;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * SYSTEM actor hooks: payment gateway callback (mark paid), unpaid-order expiry and auto-complete. Each call runs
 * the normal {@link OrderTransitionService} transaction, so the state machine, stock release and timeline apply.
 * Not scheduled yet: call from a job / webhook handler when those exist.
 */
@Service
public class OrderSystemService {
    private final OrderTransitionService transitions;
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public OrderSystemService(OrderTransitionService transitions, OrderRepository orders, PaymentRepository payments) {
        this.payments = payments;
        this.transitions = transitions;
        this.orders = orders;
    }

    /**
     * Gateway capture. Idempotent: a capture already recorded for the same provider payment id (any later order status)
     * returns changed=false; the same order captured twice is a no-op through the state machine.
     */
    public OrderTransitionService.Result paymentCaptured(UUID orderId, String provider, String reference, java.math.BigDecimal amount) {
        if (reference != null && payments.existsByProviderAndProviderPaymentId(provider, reference)) {
            var order = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("order not found"));
            boolean sameOrder = payments.findByOrderId(orderId).stream().anyMatch(p -> reference.equals(p.getProviderPaymentId()) && provider.equals(p.getProvider()));
            if (!sameOrder) throw new IllegalStateException("provider payment id belongs to another order");
            return new OrderTransitionService.Result(false, order.getStatus(), order.getStatus(), order);
        }
        return transitions.transition(orderId, OrderActor.system(), OrderTransitionService.Action.MARK_PAID,
            new OrderDtos.TransitionRequest(null, null, null, null, provider, amount, reference, null, null));
    }

    /** Cancels PENDING_PAYMENT orders placed before now - ttl; returns how many were cancelled. */
    public int expireUnpaid(Duration ttl) {
        int cancelled = 0;
        for (var id : orders.findUnpaidPlacedBefore(Instant.now().minus(ttl))) {
            try {
                var result = transitions.transition(id, OrderActor.system(), OrderTransitionService.Action.CANCEL,
                    new OrderDtos.TransitionRequest("หมดเวลาชำระเงิน", null, null, null, null, null, null, null, null));
                if (result.changed()) cancelled++;
            } catch (InvalidStatusTransitionException ignored) {
                // paid meanwhile
            }
        }
        return cancelled;
    }
}
