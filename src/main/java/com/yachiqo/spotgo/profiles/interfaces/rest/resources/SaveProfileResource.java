package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
public record SaveProfileResource(
    @Schema(description = "Person's first name. Required and cannot be blank.", example = "Juan") @NotBlank String firstName,
    @Schema(description = "Person's last name.", example = "Demo") @NotBlank String lastName,
    @Schema(description = "Contact phone number. Stored as text without country-specific format validation.", example = "900000002") @NotBlank String phone) {}
