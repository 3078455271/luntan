package com.luntan.identity.api;

import com.luntan.identity.domain.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiError.of(exception.getStatus(), exception.getCode(), exception.getMessage(), request, List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                               HttpServletRequest request) {
        List<Violation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法", request, violations));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception,
                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "DATA_CONFLICT", "数据已存在或状态发生冲突", request, List.of()));
    }

    private static Violation toViolation(FieldError error) {
        return new Violation(error.getField(), error.getDefaultMessage());
    }

    record ApiError(Instant timestamp, int status, String code, String message, String path,
                    String requestId, List<Violation> violations) {
        static ApiError of(HttpStatus status, String code, String message,
                           HttpServletRequest request, List<Violation> violations) {
            return new ApiError(
                    Instant.now(),
                    status.value(),
                    code,
                    message,
                    request.getRequestURI(),
                    request.getHeader("X-Request-Id"),
                    violations);
        }
    }

    record Violation(String field, String message) {
    }
}