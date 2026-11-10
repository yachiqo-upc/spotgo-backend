package com.yachiqo.spotgo.billing.interfaces.rest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.dao.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;
@RestControllerAdvice(basePackages="com.yachiqo.spotgo.billing.interfaces.rest") @Order(Ordered.HIGHEST_PRECEDENCE)
public class BillingExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class)
 public ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Operacion rechazada":e.getReason()));}
 @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message",e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"JSON, UUID o importe invalidos"));}
 @ExceptionHandler({DataIntegrityViolationException.class,PessimisticLockingFailureException.class})
 public ResponseEntity<?> conflict(Exception e){return ResponseEntity.status(409).body(Map.of("message","Conflicto de integridad o concurrencia; revisa claves de idempotencia, titularidad y referencias"));}
}
