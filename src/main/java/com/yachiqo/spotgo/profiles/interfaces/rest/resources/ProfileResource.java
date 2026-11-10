package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record ProfileResource(
    @Schema(description = "UUID propio del perfil (profiles.profile_id). Es distinto del ID de cuenta.", example = "00000000-0000-4000-8000-000000000012") UUID id,
    @Schema(description = "UUID de la cuenta de IAM vinculada a este perfil.", example = "00000000-0000-4000-8000-000000000002") UUID accountId,
    @Schema(description = "Nombres.", example = "Juan") String firstName,
    @Schema(description = "Apellidos.", example = "Demo") String lastName,
    @Schema(description = "Telefono de contacto.", example = "900000002") String phone,
    @Schema(description = "Instante UTC de creacion de esta fila, generado por el backend.", example = "2026-11-09T07:00:00Z") Instant createdAt,
    @Schema(description = "Instante UTC de la ultima modificacion de esta fila.", example = "2026-11-09T07:00:00Z") Instant updatedAt) {
    public static ProfileResource from(Profile p) {
        return new ProfileResource(p.getId(), p.getAccountId(), p.getFirstName(), p.getLastName(),
            p.getPhone(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
