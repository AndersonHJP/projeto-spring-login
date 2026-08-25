package com.familyti.product.exception;

import com.familyti.product.util.LoggerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex) {
        LoggerUtil.logError(this.getClass(), "handleBusiness", "Unhandled business exception: {}", ex, ex.getMessage());
        return buildResponse(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        LoggerUtil.logError(this.getClass(), "handleMethodArgumentNotValid", "Validation failed: {}", ex, fieldErrors);
        Map<String, Object> body = buildResponseBody(status, "Validation failed for one or more fields.");
        body.put("errors", fieldErrors);

        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        LoggerUtil.logError(this.getClass(), "handleEmailAlreadyExists", "Email already exists: {}", ex, ex.getMessage());
        return ResponseEntity.status(status).body(buildResponseBody(status, ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        LoggerUtil.logError(this.getClass(), "handleInvalidCredentials", "Invalid credentials: {}", ex, ex.getMessage());
        return ResponseEntity.status(status).body(buildResponseBody(status, ex.getMessage()));
    }

    private ResponseEntity<Object> buildResponse(String message) {
        Map<String, Object> body = buildResponseBody(HttpStatus.BAD_REQUEST, message);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> buildResponseBody(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
