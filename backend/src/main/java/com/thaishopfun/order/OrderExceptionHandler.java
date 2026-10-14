package com.thaishopfun.order;

import com.thaishopfun.auth.AuthDtos;
import com.thaishopfun.cart.CartDtos;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Order-specific error bodies. Scoped to the order controllers so other APIs keep their behaviour. */
@RestControllerAdvice(assignableTypes = {BuyerOrderController.class, MerchantOrderController.class, AdminOrderController.class, CheckoutController.class})
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OrderExceptionHandler {
    public record TransitionError(String code, String message, String from, String to, List<String> allowed) {}

    public record CheckoutError(String code, String message, List<AuthDtos.ApiFieldError> fieldErrors, List<CartDtos.Issue> issues) {}

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<TransitionError> transition(InvalidStatusTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new TransitionError(exception.getCode(), exception.getMessage(),
            exception.getFromStatus() == null ? null : exception.getFromStatus().name(),
            exception.getToStatus() == null ? null : exception.getToStatus().name(), exception.getAllowed()));
    }

    @ExceptionHandler(OrderCheckoutService.CheckoutInvalidException.class)
    ResponseEntity<CheckoutError> checkout(OrderCheckoutService.CheckoutInvalidException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new CheckoutError(exception.getCode(), exception.getMessage(),
            exception.getFieldErrors(), exception.getIssues()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<AuthDtos.ApiError> optimistic() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthDtos.ApiError("ORDER_VERSION_CONFLICT",
            "ออเดอร์ถูกแก้ไขพร้อมกัน กรุณาโหลดใหม่แล้วลองอีกครั้ง"));
    }
}
