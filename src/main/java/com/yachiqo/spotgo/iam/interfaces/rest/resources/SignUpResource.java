package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record SignUpResource(
    @NotBlank @Email @Schema(example = "nuevo.driver@example.com") String email,
    @NotBlank @Schema(description = "Minimo 8 caracteres con mayuscula, minuscula, numero y simbolo. Nunca se guarda en claro.", example = "Prueba123!", accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}
