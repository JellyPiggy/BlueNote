package com.bluenote.common.web;

import com.bluenote.common.core.ApiErrorCode;
import com.bluenote.common.core.ApiResponse;
import com.bluenote.common.core.BusinessException;
import com.bluenote.common.observability.TraceIdHolder;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.ok(ApiResponse.failure(
                exception.errorCode(),
                exception.errorData(),
                TraceIdHolder.currentOrNew()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<Map<String, String>> fields = exception.getBindingResult().getFieldErrors()
                .stream()
                .map(this::fieldError)
                .toList();
        Map<String, Object> data = Map.of(
                "reason", ApiErrorCode.PARAM_INVALID.reason(),
                "fields", fields
        );
        return ResponseEntity.ok(ApiResponse.failure(ApiErrorCode.PARAM_INVALID, data, TraceIdHolder.currentOrNew()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, Object> data = Map.of(
                "reason", ApiErrorCode.PARAM_INVALID.reason(),
                "violations", exception.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .toList()
        );
        return ResponseEntity.ok(ApiResponse.failure(ApiErrorCode.PARAM_INVALID, data, TraceIdHolder.currentOrNew()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure(ApiErrorCode.METHOD_NOT_ALLOWED, TraceIdHolder.currentOrNew()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception exception) {
        log.error("Unhandled request exception", exception);
        return ResponseEntity.ok(ApiResponse.failure(ApiErrorCode.SYSTEM_ERROR, TraceIdHolder.currentOrNew()));
    }

    private Map<String, String> fieldError(FieldError fieldError) {
        return Map.of(
                "field", fieldError.getField(),
                "message", fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage()
        );
    }
}
