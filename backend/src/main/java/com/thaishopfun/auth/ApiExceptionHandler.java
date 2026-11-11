package com.thaishopfun.auth;

import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.catalog.ImportRunningException;
import com.thaishopfun.common.ApiValidationError;
import com.thaishopfun.common.IntegritySqlStates;
import com.thaishopfun.common.ValidationException;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        String section = sectionFor(exception.getCode(), fieldErrors);
        return ResponseEntity.status(exception.getStatus()).body(new AuthDtos.ApiError(
            exception.getCode(), exception.getMessage(), fieldErrors, section, null));
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
            .map(error -> new AuthDtos.ApiFieldError(
                error.getField(),
                constraintCode(error),
                safeMessage(error.getDefaultMessage()),
                sizeParams(error)))
            .toList();
        var message = fieldErrors.isEmpty() ? "ข้อมูลไม่ถูกต้อง" : fieldErrors.get(0).message();
        String section = sectionFor(null, fieldErrors);
        return ResponseEntity.badRequest().body(new AuthDtos.ApiError(
            "VALIDATION_FAILED", message, fieldErrors.isEmpty() ? null : fieldErrors, section, null));
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

    /**
     * Integrity failures that slipped past service-level checks.
     * Classified by SQLState — never expose SQL/table/column/values to clients.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<AuthDtos.ApiError> integrity(DataIntegrityViolationException exception) {
        String requestId = newRequestId();
        String sqlState = IntegritySqlStates.sqlState(exception);
        String diagnostic = IntegritySqlStates.safeDiagnostic(exception);
        log.warn("Data integrity violation requestId={} sqlState={} detail={}", requestId, sqlState, diagnostic);

        String section = onboardingSectionFromPath();
        return switch (sqlState) {
            case IntegritySqlStates.UNIQUE -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthDtos.ApiError(
                    "CONFLICT",
                    "ข้อมูลซ้ำหรือขัดแย้งกับข้อมูลที่มีอยู่ กรุณาลองใหม่",
                    null,
                    section,
                    requestId));
            case IntegritySqlStates.VALUE_TOO_LONG -> {
                String message = section == null
                    ? "ข้อมูลบางช่องยาวเกินกำหนด"
                    : "ข้อมูลบางช่องในส่วน " + sectionLabelTh(section) + " ยาวเกินกำหนด";
                yield ResponseEntity.badRequest().body(new AuthDtos.ApiError(
                    "VALUE_TOO_LONG",
                    message,
                    null,
                    section,
                    requestId));
            }
            case IntegritySqlStates.NOT_NULL -> ResponseEntity.badRequest().body(new AuthDtos.ApiError(
                "REQUIRED_MISSING",
                "ข้อมูลที่จำเป็นไม่ครบถ้วน กรุณาตรวจสอบแล้วลองใหม่",
                null,
                section,
                requestId));
            case IntegritySqlStates.FOREIGN_KEY -> ResponseEntity.badRequest().body(new AuthDtos.ApiError(
                "REFERENCE_INVALID",
                "ข้อมูลอ้างอิงไม่ถูกต้อง กรุณาตรวจสอบแล้วลองใหม่",
                null,
                section,
                requestId));
            default -> ResponseEntity.badRequest().body(new AuthDtos.ApiError(
                "DATA_INTEGRITY",
                "ไม่สามารถบันทึกข้อมูลได้ กรุณาตรวจสอบแล้วลองใหม่",
                null,
                section,
                requestId));
        };
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
        String requestId = newRequestId();
        log.error("Database unavailable requestId={} type={}", requestId, exception.getClass().getSimpleName(), exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new AuthDtos.ApiError("DATABASE_UNAVAILABLE", "ฐานข้อมูลใช้งานไม่ได้", null, null, requestId));
    }

    /** Other DataAccessException kinds (EmptyResult, BadSqlGrammar, etc.) — 500, no JDBC/secrets in body. */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AuthDtos.ApiError> otherDataAccess(DataAccessException exception) {
        String requestId = newRequestId();
        log.error("Data access error requestId={} type={}", requestId, exception.getClass().getSimpleName(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new AuthDtos.ApiError(
                "INTERNAL_ERROR",
                "ระบบขัดข้องชั่วคราว กรุณาลองใหม่อีกครั้ง",
                null,
                null,
                requestId));
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

    private static String sectionFor(String code, List<AuthDtos.ApiFieldError> fieldErrors) {
        if ("SHOP_EXISTS".equals(code)) return "shop";
        if ("CITIZEN_ID_TAKEN".equals(code) || "OWNER_EXISTS".equals(code)) return "owner";
        String fromPath = onboardingSectionFromPath();
        if (fromPath != null) return fromPath;
        if (fieldErrors == null || fieldErrors.isEmpty()) return null;
        String field = fieldErrors.get(0).field();
        if (field == null) return null;
        if (field.startsWith("bank") || "bankName".equals(field)) return "bank";
        if (field.equals("address") || field.equals("houseNo") || field.equals("villageNo")
            || field.equals("building") || field.equals("alley") || field.equals("road")
            || field.equals("province") || field.equals("district") || field.equals("subdistrict")
            || field.equals("postalCode")) {
            return onboardingSectionFromPath() != null ? onboardingSectionFromPath() : "address";
        }
        if (field.equals("shopName") || field.equals("shopNameEn") || field.equals("description")
            || field.equals("category") || field.equals("saleType") || field.equals("website")
            || field.equals("lineId") || field.equals("facebookUrl") || field.equals("instagramUrl")
            || field.equals("tiktokUrl")) {
            return "shop";
        }
        return "owner";
    }

    private static String onboardingSectionFromPath() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servlet)) return null;
        String path = servlet.getRequest().getRequestURI();
        if (path == null) return null;
        if (path.contains("/draft/shop")) return "shop";
        if (path.contains("/draft/owner") || path.contains("validate-owner")) return "owner";
        if (path.contains("/documents")) return "documents";
        if (path.contains("/draft/submit")) return "shop";
        return null;
    }

    private static String sectionLabelTh(String section) {
        return switch (section) {
            case "shop" -> "ข้อมูลร้านค้า";
            case "owner" -> "ข้อมูลเจ้าของ";
            case "documents" -> "เอกสาร";
            case "address" -> "ที่อยู่";
            case "bank" -> "บัญชีรับเงิน";
            default -> section;
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

    private static Map<String, Object> sizeParams(FieldError error) {
        if (!"Size".equals(error.getCode())) return null;
        Object[] args = error.getArguments();
        if (args == null || args.length < 3) return null;
        // Hibernate/Spring Size args: [payload, max, min] (order can vary); prefer numeric max.
        Integer max = null;
        for (Object arg : args) {
            if (arg instanceof Number number) {
                int value = number.intValue();
                if (max == null || value > max) max = value;
            }
        }
        if (max == null) return null;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("max", max);
        return params;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) return "ข้อมูลไม่ถูกต้อง";
        if (message.contains(":\\") || message.contains("Exception") || message.contains("\n")) return "ข้อมูลไม่ถูกต้อง";
        return message;
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
