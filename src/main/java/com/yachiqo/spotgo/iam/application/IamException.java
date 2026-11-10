package com.yachiqo.spotgo.iam.application;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** Controlled rejection; failures are audited without rolling back those audit records. */
public class IamException extends ResponseStatusException {
    public IamException(HttpStatus status, String reason) { super(status, reason); }
}
