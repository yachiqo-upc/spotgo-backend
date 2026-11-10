package com.yachiqo.spotgo.billing.application;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public class BillingException extends ResponseStatusException {
 public BillingException(HttpStatus status,String reason){super(status,reason);}
}
