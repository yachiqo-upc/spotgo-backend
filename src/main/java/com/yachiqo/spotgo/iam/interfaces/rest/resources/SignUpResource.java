package com.yachiqo.spotgo.iam.interfaces.rest.resources;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignUpResource(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phone,
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
