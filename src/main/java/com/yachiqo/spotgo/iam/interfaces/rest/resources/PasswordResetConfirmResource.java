package com.yachiqo.spotgo.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmResource(
        @Schema(description = "Account email", example = "client@spotgo.com")
        @NotBlank
        @Email
        String email,
        @Schema(description = "One-time reset code", example = "123456")
        @NotBlank
        String code,
        @Schema(description = "New password", example = "Password123!")
        @NotBlank
        String newPassword
) {
}
