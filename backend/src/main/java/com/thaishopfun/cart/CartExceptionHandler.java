package com.thaishopfun.cart;

import com.thaishopfun.auth.AuthDtos;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CartExceptionHandler {
    public record StockError(String code, String message, List<AuthDtos.ApiFieldError> fieldErrors, UUID skuId,
                             int requestedQuantity, int availableQuantity, int inCartQuantity, int canAddQuantity) {}

    @ExceptionHandler(CartStockException.class)
    ResponseEntity<StockError> stock(CartStockException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new StockError(exception.getCode(), exception.getMessage(),
            exception.getFieldErrors(), exception.getSkuId(), exception.getRequestedQuantity(), exception.getAvailableQuantity(),
            exception.getInCartQuantity(), Math.max(0, exception.getAvailableQuantity() - exception.getInCartQuantity())));
    }
}
