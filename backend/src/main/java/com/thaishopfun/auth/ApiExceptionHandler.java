package com.thaishopfun.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthException.class)
    ResponseEntity<AuthDtos.ApiError> auth(AuthException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new AuthDtos.ApiError(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<AuthDtos.ApiError> credentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthDtos.ApiError("INVALID_CREDENTIALS", "อีเมล เบอร์โทรศัพท์ หรือรหัสผ่านไม่ถูกต้อง"));
    }

    @ExceptionHandler(DisabledException.class)
    ResponseEntity<AuthDtos.ApiError> disabled() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthDtos.ApiError("ACCOUNT_DISABLED", "บัญชีถูกระงับการใช้งาน"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AuthDtos.ApiError> validation(MethodArgumentNotValidException exception) {
        var error = exception.getBindingResult().getFieldErrors().stream().findFirst();
        var message = error.map(e -> e.getDefaultMessage()).orElse("ข้อมูลไม่ถูกต้อง");
        return ResponseEntity.badRequest().body(new AuthDtos.ApiError("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<AuthDtos.ApiError> uploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new AuthDtos.ApiError("FILE_TOO_LARGE", "ไฟล์ต้องมีขนาดไม่เกิน 5 MB"));
    }
}