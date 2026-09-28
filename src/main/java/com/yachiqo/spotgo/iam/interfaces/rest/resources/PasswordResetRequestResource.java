package com.yachiqo.spotgo.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequestResource(
        @Schema(description = "Account email", example = "client@spotgo.com")
        @NotBlank
        @Email
        String email
) {
}
