package com.yachiqo.spotgo.iam.infrastructure.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.yachiqo.spotgo.iam.application.GoogleIdentity;
import com.yachiqo.spotgo.iam.application.IamException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/** Verifies Google-issued ID tokens before IAM accepts their subject or email. */
@Component
public class GoogleIdentityVerifier {
    private final String clientId;
    private volatile GoogleIdTokenVerifier verifier;

    public GoogleIdentityVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public GoogleIdentity verify(String idToken) {
        if (clientId.isBlank()) {
            throw new IamException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured.");
        }
        try {
            GoogleIdToken token = verifier().verify(idToken);
            if (token == null) throw invalidToken();
            GoogleIdToken.Payload payload = token.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) throw invalidToken();
            return new GoogleIdentity(payload.getSubject(), payload.getEmail());
        } catch (IamException exception) {
            throw exception;
        } catch (GeneralSecurityException | IOException exception) {
            throw new IamException(HttpStatus.SERVICE_UNAVAILABLE, "Google token verification is temporarily unavailable.");
        }
    }

    private GoogleIdTokenVerifier verifier() throws GeneralSecurityException, IOException {
        var current = verifier;
        if (current != null) return current;
        synchronized (this) {
            if (verifier == null) {
                verifier = new GoogleIdTokenVerifier.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(List.of(clientId))
                        .build();
            }
            return verifier;
        }
    }

    private static IamException invalidToken() {
        return new IamException(HttpStatus.UNAUTHORIZED, "Google ID token is invalid, expired, or not verified for this application.");
    }
}
