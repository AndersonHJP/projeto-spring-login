package com.familyti.product.exception;

import com.familyti.product.util.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final DataSize maxFileSize;

    public GlobalExceptionHandler(@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
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
        return respond(HttpStatus.CONFLICT, "handleEmailAlreadyExists", "Email already exists", ex, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "handleInvalidCredentials", "Invalid credentials", ex, ex.getMessage());
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
        return respond(HttpStatus.FORBIDDEN, "handleAccessDenied", "Access denied", ex,
                "You do not have permission to access this resource.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "handleResourceNotFound", "Resource not found", ex, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<Map<String, Object>> handleForbiddenOperation(ForbiddenOperationException ex) {
        return respond(HttpStatus.FORBIDDEN, "handleForbiddenOperation", "Forbidden", ex, ex.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFile(InvalidFileException ex) {
        return respond(HttpStatus.BAD_REQUEST, "handleInvalidFile", "Invalid file", ex, ex.getMessage());
    }

    @ExceptionHandler(InvalidStorageProviderException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStorageProvider(InvalidStorageProviderException ex) {
        return respond(HttpStatus.BAD_REQUEST, "handleInvalidStorageProvider", "Invalid storage provider", ex,
                ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "handleMaxUploadSize", "Upload too large", ex,
                "O arquivo excede o limite de " + humanReadable(maxFileSize) + ".");
    }

    private static String humanReadable(DataSize size) {
        long megabytes = size.toMegabytes();
        return megabytes > 0 ? megabytes + " MB" : size.toKilobytes() + " KB";
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorage(StorageException ex) {
        return respond(HttpStatus.BAD_GATEWAY, "handleStorage", "Storage failure", ex,
                "Não foi possível processar o arquivo no momento. Tente novamente.");
    }

    private ResponseEntity<Map<String, Object>> respond(HttpStatus status, String handler, String logLabel,
                                                        Exception ex, String clientMessage) {
        LoggerUtil.logError(this.getClass(), handler, logLabel + ": {}", ex, ex.getMessage());
        return ResponseEntity.status(status).body(buildResponseBody(status, clientMessage));
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