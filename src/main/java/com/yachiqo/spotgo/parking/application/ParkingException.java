package com.yachiqo.spotgo.parking.application;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public class ParkingException extends ResponseStatusException {
 public ParkingException(HttpStatus status,String reason) { super(status,reason); }
}
