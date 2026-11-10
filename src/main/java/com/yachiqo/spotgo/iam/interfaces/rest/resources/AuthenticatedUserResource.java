package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
public record AuthenticatedUserResource(
    @Schema(description = "UUID de la cuenta; no es el UUID del perfil") UUID accountId,
    String email, UserRole role, UUID tenantId, UUID sessionId,
    @Schema(description = "JWT de corta duracion. Copia este valor en Authorize", accessMode = Schema.AccessMode.READ_ONLY) String accessToken,
    @Schema(description = "Token opaco para renovar. No es un JWT ni se coloca en Authorize; se rota en cada refresh", accessMode = Schema.AccessMode.READ_ONLY) String refreshToken,
    String tokenType, Instant accessExpiresAt, Instant refreshExpiresAt) {}
