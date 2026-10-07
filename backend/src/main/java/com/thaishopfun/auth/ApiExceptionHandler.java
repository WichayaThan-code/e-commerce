package com.thaishopfun.auth;

<<<<<<< HEAD
import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.catalog.ImportRunningException;
=======
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
>>>>>>> 232b7724b5084e8924c048593b686e77c9566a9d
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
<<<<<<< HEAD
    @ExceptionHandler(ImportRunningException.class)
    ResponseEntity<CatalogDtos.ProductImportConflict> importRunning(ImportRunningException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new CatalogDtos.ProductImportConflict("IMPORT_RUNNING", exception.getMessage(), exception.jobId()));
    }
=======
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
>>>>>>> 232b7724b5084e8924c048593b686e77c9566a9d

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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<AuthDtos.ApiError> uploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new AuthDtos.ApiError("FILE_TOO_LARGE", "ไฟล์ต้องมีขนาดไม่เกิน 5 MB"));
    }
}
