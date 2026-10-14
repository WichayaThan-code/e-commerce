package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 409 when from→to is not in {@link OrderStateMachine}. Body includes from, to, allowed[]. */
public class InvalidStatusTransitionException extends AuthException {
    private final OrderStatus from;
    private final OrderStatus to;
    private final List<String> allowed;

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to, Set<OrderStatus> allowed, String code) {
        super(HttpStatus.CONFLICT, code == null ? "INVALID_STATUS_TRANSITION" : code,
            "เปลี่ยนสถานะจาก " + from + " ไป " + to + " ไม่ได้");
        this.from = from;
        this.to = to;
        this.allowed = allowed == null ? List.of() : allowed.stream().map(Enum::name).toList();
    }

    public OrderStatus getFromStatus() { return from; }
    public OrderStatus getToStatus() { return to; }
    public List<String> getAllowed() { return allowed; }

    public Map<String, Object> details() {
        var map = new LinkedHashMap<String, Object>();
        map.put("from", from == null ? null : from.name());
        map.put("to", to == null ? null : to.name());
        map.put("allowed", allowed);
        return map;
    }
}
