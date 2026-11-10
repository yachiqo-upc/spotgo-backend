package com.yachiqo.spotgo.monitoring.application;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public class MonitoringException extends ResponseStatusException {
 public MonitoringException(HttpStatus status,String reason){super(status,reason);}
}
