package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record SignUpResource(
    @NotBlank @Email @Schema(example = "nuevo.driver@example.com") String email,
    @NotBlank @Schema(description = "At least 8 characters, including an uppercase letter, a lowercase letter, a number, and a symbol. Never stored in plain text.", example = "Prueba123!", accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}
