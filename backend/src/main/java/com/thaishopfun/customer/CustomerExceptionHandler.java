package com.thaishopfun.customer;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CustomerExceptionHandler {
    @ExceptionHandler(DuplicateCustomerException.class)
    ResponseEntity<CustomerDtos.DuplicateCustomerError> duplicate(DuplicateCustomerException exception) {
        var matches = exception.getMatches();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new CustomerDtos.DuplicateCustomerError(
            DuplicateCustomerException.CODE, exception.getMessage(), matches.isEmpty() ? null : matches.get(0).id(), matches));
    }
}
