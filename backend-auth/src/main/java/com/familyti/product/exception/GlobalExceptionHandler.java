package com.familyti.product.exception;

import com.familyti.product.util.LoggerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String message = (ex instanceof InsufficientAuthenticationException)
                ? "Authentication required. Provide a valid Bearer token in the Authorization header."
                : ex.getMessage();
        LoggerUtil.logError(this.getClass(), "handleAuthentication", "Authentication failed: {}", ex, message);
        return ResponseEntity.status(status).body(buildResponseBody(status, message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        LoggerUtil.logError(this.getClass(), "handleAccessDenied", "Access denied: {}", ex, ex.getMessage());
        return ResponseEntity.status(status)
                .body(buildResponseBody(status, "You do not have permission to access this resource."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        LoggerUtil.logError(this.getClass(), "handleResourceNotFound", "Resource not found: {}", ex, ex.getMessage());
        return ResponseEntity.status(status).body(buildResponseBody(status, ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<Map<String, Object>> handleForbiddenOperation(ForbiddenOperationException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        LoggerUtil.logError(this.getClass(), "handleForbiddenOperation", "Forbidden: {}", ex, ex.getMessage());
        return ResponseEntity.status(status).body(buildResponseBody(status, ex.getMessage()));
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFile(InvalidFileException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        LoggerUtil.logError(this.getClass(), "handleInvalidFile", "Invalid file: {}", ex, ex.getMessage());
        return ResponseEntity.status(status).body(buildResponseBody(status, ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        LoggerUtil.logError(this.getClass(), "handleMaxUploadSize", "Upload too large: {}", ex, ex.getMessage());
        return ResponseEntity.status(status)
                .body(buildResponseBody(status, "O arquivo excede o limite de 5 MB."));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorage(StorageException ex) {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        LoggerUtil.logError(this.getClass(), "handleStorage", "Storage failure: {}", ex, ex.getMessage());
        return ResponseEntity.status(status).body(buildResponseBody(status,
                "Não foi possível processar o arquivo no momento. Tente novamente."));
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