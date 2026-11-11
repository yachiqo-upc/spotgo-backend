package com.yachiqo.spotgo.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleIdTokenResource(
        @NotBlank
        @Size(max = 8192)
        @Schema(description = "Google ID token returned by the official sign-in flow. The backend verifies its signature, issuer, audience, expiry, and email verification; never send a plain Google user ID.", accessMode = Schema.AccessMode.WRITE_ONLY)
        String idToken) {}
