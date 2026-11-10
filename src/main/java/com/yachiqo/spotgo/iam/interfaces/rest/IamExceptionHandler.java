package com.yachiqo.spotgo.iam.interfaces.rest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.yachiqo.spotgo.iam.interfaces.rest")
public class IamExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> rejection(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("status", ex.getStatusCode().value(),
            "message", ex.getReason() == null ? "Solicitud rechazada" : ex.getReason()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", 409, "message", "Duplicate or invalid email, external identity, role assignment, or reference."));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", "Invalid UUID, enum, or JSON."));
    }
}
