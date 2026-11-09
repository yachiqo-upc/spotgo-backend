package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
public record SaveProfileResource(
    @Schema(description = "Nombres de la persona. Obligatorio y sin texto vacio.", example = "Juan") @NotBlank String firstName,
    @Schema(description = "Apellidos de la persona.", example = "Demo") @NotBlank String lastName,
    @Schema(description = "Telefono de contacto. Se guarda como texto; no tiene validacion de formato nacional.", example = "900000002") @NotBlank String phone) {}
