package com.yachiqo.spotgo.monitoring.interfaces.rest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.dao.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;
@RestControllerAdvice(basePackages="com.yachiqo.spotgo.monitoring.interfaces.rest") @Order(Ordered.HIGHEST_PRECEDENCE)
public class MonitoringExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class)
 public ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Operacion rechazada":e.getReason()));}
 @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message",e instanceof IllegalArgumentException?e.getMessage():"Invalid JSON, UUID, or timestamp."));}
 @ExceptionHandler({DataIntegrityViolationException.class,PessimisticLockingFailureException.class})
 public ResponseEntity<?> integrity(Exception e){return ResponseEntity.status(409).body(Map.of("message","Integrity or concurrency conflict. Check references and active sensors."));}
}
