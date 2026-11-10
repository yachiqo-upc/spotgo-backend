package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record SignInResource(
    @NotBlank @Email @Schema(example = "juan.driver@example.com") String email,
    @NotBlank @Schema(example = "Password123!", accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}
