package com.thaishopfun;

import com.thaishopfun.order.OrderActor;
import com.thaishopfun.order.OrderBulkRequest;
import com.thaishopfun.order.OrderBulkRequestRepository;
import com.thaishopfun.order.OrderBulkService;
import com.thaishopfun.order.OrderDtos;
import com.thaishopfun.order.OrderTransitionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P0: bulk failures never leak raw exception text; a concurrent duplicate Idempotency-Key replays instead of 500. */
class OrderBulkServiceTests {
    private final UUID shop = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final OrderBulkRequestRepository repo = mock(OrderBulkRequestRepository.class);
    private final OrderTransitionService transitions = mock(OrderTransitionService.class);
    private final OrderBulkService service = new OrderBulkService(repo, transitions, null, mock(PlatformTransactionManager.class));

    private OrderDtos.BulkRequest accept(UUID... ids) {
        return new OrderDtos.BulkRequest(java.util.Arrays.stream(ids).map(id -> new OrderDtos.BulkItem(id, 1)).toList(),
            null, null, null, null);
    }

    @Test
    void rawExceptionTextNeverReachesResponseOrStoredRow() {
        var raw = UUID.randomUUID();
        var locked = UUID.randomUUID();
        when(repo.findByShopIdAndActorUserIdAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
        when(transitions.transition(eq(raw), any(), any(), any()))
            .thenThrow(new IllegalStateException("ERROR: relation orders constraint fk_secret_internal"));
        when(transitions.transition(eq(locked), any(), any(), any()))
            .thenThrow(new ObjectOptimisticLockingFailureException("Order", locked));
        var stored = new OrderBulkRequest[1];
        when(repo.saveAndFlush(any())).thenAnswer(inv -> stored[0] = inv.getArgument(0));

        var response = service.runAction(shop, user, OrderActor.merchant(user, shop), "accept", accept(raw, locked), "k-raw-0001");

        assertEquals(2, response.failed());
        assertEquals("REQUEST_FAILED", response.results().get(0).code());
        assertEquals("ORDER_VERSION_CONFLICT", response.results().get(1).code());
        response.results().forEach(r -> assertFalse(String.valueOf(r.message()).contains("fk_secret_internal")));
        assertFalse(String.valueOf(stored[0].getResponse()).contains("fk_secret_internal"));
        assertFalse(String.valueOf(stored[0].getResponse()).contains("ObjectOptimistic"));
    }

    @Test
    void concurrentDuplicateKeyReplaysStoredResponseInsteadOf500() {
        var id = UUID.randomUUID();
        var stored = new OrderBulkRequest[1];
        when(transitions.transition(eq(id), any(), any(), any()))
            .thenThrow(new ObjectOptimisticLockingFailureException("Order", id));
        // first lookup: nothing yet; after the unique violation the winner's row is visible
        when(repo.findByShopIdAndActorUserIdAndIdempotencyKey(any(), any(), any()))
            .thenReturn(Optional.empty())
            .thenAnswer(inv -> Optional.of(stored[0]));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            stored[0] = inv.getArgument(0);
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint ux_order_bulk_requests");
        });

        var response = service.runAction(shop, user, OrderActor.merchant(user, shop), "accept", accept(id), "k-dup-0001");

        assertTrue(response.replayed());
        assertEquals(1, response.total());
        assertEquals(List.of("ORDER_VERSION_CONFLICT"), response.results().stream().map(OrderDtos.BulkResultItem::code).toList());
    }
}
