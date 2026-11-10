package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
public record AuthenticatedUserResource(
    @Schema(description = "Account UUID; this is not the profile UUID.") UUID accountId,
    String email, UserRole role, UUID tenantId, UUID sessionId,
    @Schema(description = "Short-lived JWT. Paste this value into Swagger Authorize.", accessMode = Schema.AccessMode.READ_ONLY) String accessToken,
    @Schema(description = "Opaque token used to refresh a session. It is not a JWT and must not be entered in Swagger Authorize; it is rotated on every refresh.", accessMode = Schema.AccessMode.READ_ONLY) String refreshToken,
    String tokenType, Instant accessExpiresAt, Instant refreshExpiresAt) {}
