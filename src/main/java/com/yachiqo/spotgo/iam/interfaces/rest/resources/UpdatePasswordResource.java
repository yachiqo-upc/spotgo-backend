package com.yachiqo.spotgo.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record UpdatePasswordResource(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}
