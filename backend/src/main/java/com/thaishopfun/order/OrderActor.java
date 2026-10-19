package com.thaishopfun.order;

import java.util.UUID;

/** Who is acting and through which door (buyer API, shop API with shop uuid, admin API, or the system). */
public record OrderActor(UUID userId, OrderActorType type, UUID shopUuid) {
    public static OrderActor buyer(UUID userId) { return new OrderActor(userId, OrderActorType.BUYER, null); }
    public static OrderActor merchant(UUID userId, UUID shopUuid) { return new OrderActor(userId, OrderActorType.MERCHANT, shopUuid); }
    public static OrderActor admin(UUID userId) { return new OrderActor(userId, OrderActorType.ADMIN, null); }
    public static OrderActor system() { return new OrderActor(null, OrderActorType.SYSTEM, null); }

    /** Whether this actor may see the order at all (others get 404, existence not revealed). */
    public boolean canSee(Order order) {
        return switch (type) {
            case BUYER -> userId != null && userId.equals(order.getBuyerUserId());
            case MERCHANT -> shopUuid != null && shopUuid.equals(order.getShopId());
            case ADMIN, SYSTEM -> true;
        };
    }
}
