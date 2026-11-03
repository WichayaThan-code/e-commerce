package com.thaishopfun.auth;

import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.catalog.ImportRunningException;
import com.thaishopfun.common.ApiValidationError;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.ValidationMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ImportRunningException.class)
    ResponseEntity<CatalogDtos.ProductImportConflict> importRunning(ImportRunningException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new CatalogDtos.ProductImportConflict("IMPORT_RUNNING", exception.getMessage(), exception.jobId()));
    }

    /** Field-level validation failure from a service (more specific than AuthException, so it wins). */
    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiValidationError> fieldValidation(ValidationException exception) {
        var first = exception.getViolations().isEmpty() ? exception.getMessage() : exception.getViolations().get(0).message();
        return ResponseEntity.status(exception.getStatus()).body(new ApiValidationError(exception.getCode(), first, exception.getViolations()));
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<AuthDtos.ApiError> auth(AuthException exception) {
        List<AuthDtos.ApiFieldError> fieldErrors = exception.getFieldErrors();
        if (fieldErrors == null) {
            var field = exception.getField() != null ? exception.getField() : fieldFor(exception.getCode());
            fieldErrors = field == null ? null : List.of(new AuthDtos.ApiFieldError(field, exception.getCode(), exception.getMessage()));
        }
        return ResponseEntity.status(exception.getStatus()).body(new AuthDtos.ApiError(exception.getCode(), exception.getMessage(), fieldErrors));
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
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new AuthDtos.ApiFieldError(error.getField(), constraintCode(error), safeMessage(error.getDefaultMessage())))
            .toList();
        var message = fieldErrors.isEmpty() ? "ข้อมูลไม่ถูกต้อง" : fieldErrors.get(0).message();
        return ResponseEntity.badRequest().body(new AuthDtos.ApiError("VALIDATION_FAILED", message, fieldErrors.isEmpty() ? null : fieldErrors));
    }

    /** Malformed / unreadable JSON body (e.g. truncated JSON, wrong enum value). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AuthDtos.ApiError> unreadable() {
        return ResponseEntity.badRequest().body(new AuthDtos.ApiError("VALIDATION_ERROR", "รูปแบบข้อมูลไม่ถูกต้อง"));
    }

    /** Path/query parameter of the wrong type (e.g. /documents/NOT_A_TYPE, shopId=abc, bad UUID). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<AuthDtos.ApiError> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new AuthDtos.ApiError("VALIDATION_ERROR", "ค่าพารามิเตอร์ " + exception.getName() + " ไม่ถูกต้อง"));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<AuthDtos.ApiError> missingParameter() {
        return ResponseEntity.badRequest().body(new AuthDtos.ApiError("VALIDATION_ERROR", "ข้อมูลที่จำเป็นไม่ครบถ้วน"));
    }

    /** Unique/foreign-key violations that slipped past service-level checks (e.g. concurrent inserts). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<AuthDtos.ApiError> conflict(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthDtos.ApiError("CONFLICT", "ข้อมูลซ้ำหรือขัดแย้งกับข้อมูลที่มีอยู่ กรุณาลองใหม่"));
    }

    /**
     * DB connection failures only (not EmptyResult / BadSqlGrammar / optimistic lock, etc.).
     * Log keeps the stack; response body never includes JDBC URL, host, or stack traces.
     */
    @ExceptionHandler({
        DataAccessResourceFailureException.class,
        TransientDataAccessResourceException.class,
        QueryTimeoutException.class,
        DatabaseUnavailableException.class
    })
    ResponseEntity<AuthDtos.ApiError> databaseUnavailable(Exception exception) {
        log.error("Database unavailable: {}", exception.getClass().getSimpleName(), exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new AuthDtos.ApiError("DATABASE_UNAVAILABLE", "ฐานข้อมูลใช้งานไม่ได้"));
    }

    /** Other DataAccessException kinds (EmptyResult, BadSqlGrammar, etc.) — 500, no JDBC/secrets in body. */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AuthDtos.ApiError> otherDataAccess(DataAccessException exception) {
        log.error("Data access error: {}", exception.getClass().getSimpleName(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new AuthDtos.ApiError("INTERNAL_ERROR", "เกิดข้อผิดพลาด กรุณาลองใหม่"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<AuthDtos.ApiError> uploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new AuthDtos.ApiError("FILE_TOO_LARGE", "ไฟล์ต้องมีขนาดไม่เกิน 5 MB"));
    }

    private static String fieldFor(String code) {
        return switch (code) {
            case "CITIZEN_ID_TAKEN", "OWNER_EXISTS" -> "citizenId";
            case "SHOP_EXISTS" -> "shopName";
            default -> null;
        };
    }

    private static String constraintCode(FieldError error) {
        var code = error.getCode();
        if (code == null) return "INVALID";
        return switch (code) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Size" -> "TOO_LONG";
            case "Pattern", "Email" -> "INVALID";
            default -> code;
        };
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) return "ข้อมูลไม่ถูกต้อง";
        if (message.contains(":\\") || message.contains("Exception") || message.contains("\n")) return "ข้อมูลไม่ถูกต้อง";
        return message;
    }
}
