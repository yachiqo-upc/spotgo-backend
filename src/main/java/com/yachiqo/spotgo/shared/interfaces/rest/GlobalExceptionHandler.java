package com.yachiqo.spotgo.shared.interfaces.rest;

import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.interfaces.rest.transform.ErrorResponseAssembler;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import java.text.MessageFormat;
import java.util.UUID;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@RestControllerAdvice
@NullMarked
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MESSAGES_BASENAME = "messages";

    private String resolveMessageOrDefault(String key, String defaultValue, Object... args){
        try {
            var bundle = ResourceBundle.getBundle(MESSAGES_BASENAME, LocaleContextHolder.getLocale());
            if (!bundle.containsKey(key)) {
                return defaultValue;
            }
            return MessageFormat.format(bundle.getString(key), args);
        }
        catch (MissingResourceException ex) {
            return defaultValue;
        }
    }

    /**
     * Global exception handler for REST API
     * Provides centralized exception handling for the entire application,
     * ensuring all unhandled exceptions are translated to consistent
     * HTTP responses via the shared error assembly pattern
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors();
        var validationPrefix = resolveMessageOrDefault("validation.field.prefix", "Field");
        var errorDetails = fieldErrors.isEmpty()
                ? resolveMessageOrDefault("validation.request.failed", "Request validation failed.")
                : fieldErrors.stream()
                    .map(error -> "%s %s: %s".formatted(
                            validationPrefix,
                            error.getField(),
                            error.getDefaultMessage()
                    ))
                  .reduce((a, b) -> a + "; " + b)
                  .orElse(resolveMessageOrDefault("validation.request.failed", "Request validation failed."));
        var applicationError = ApplicationError.validationError("request-body", errorDetails);
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    /**
     * Handles invalid request argument such as malformed UUID path or payload values
     * @param ex the illegal argument exception
     * @return error response with BAD_REQUEST status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        var applicationError = ApplicationError.validationError(
                resolveMessageOrDefault("validation.request.argument", "request-argument"),
                ex.getMessage() != null ? ex.getMessage() : resolveMessageOrDefault("validation.request.invalid", "Invalid request argument.")
        );
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    /**
     * Handles access denied exceptions thrown by Spring Security
     * @param ex the access denied exception
     * @return error response with FORBIDDEN status
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        var applicationError = ApplicationError.unexpected(
                "access-denied",
                "You do not have permission to perform this action.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError).getBody());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<?> handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        log.debug("Authorization denied: {}", ex.getMessage());
        var applicationError = ApplicationError.unexpected(
                "access-denied",
                "You do not have permission to perform this action.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError).getBody());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                      HttpServletRequest request) {
        log.warn("Unsupported {} request to {}", request.getMethod(), request.getRequestURI());
        var applicationError = ApplicationError.validationError(
                "request-method",
                "Request method '%s' is not supported".formatted(ex.getMethod()));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError).getBody());
    }

    /**
     * Handles unexpected runtime exceptions not caught by specific handlers
     * Maps to a generic unexpected error response
     * @param ex the unhandled runtime exception
     * @return error response with INTERNAL_SERVER_ERROR status
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        var traceId = UUID.randomUUID().toString();
        log.error("Unhandled runtime exception [{}]: {}", traceId, ex.getMessage(), ex);
        var applicationError = ApplicationError.unexpected(
                resolveMessageOrDefault("error.unexpected.context", "global-exception-handler"),
                "An unexpected error occurred. Reference: %s".formatted(traceId));
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    /**
     * Handles all other exceptions not matched by specific handlers
     * Provide a final fallback for any unexpected exception type.
     * @param ex the exception
     * @return error response with INTERNAL_SERVER_ERROR status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        var traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception [{}]: {}", traceId, ex.getMessage(), ex);
        var applicationError = ApplicationError.unexpected(
                resolveMessageOrDefault("error.unexpected.context", "global-exception-handler"),
                "An unexpected error occurred. Reference: %s".formatted(traceId));
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }
}
