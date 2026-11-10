package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
public record RefreshTokenResource(@NotBlank @Schema(description = "refreshToken recibido en sign-in o en la ultima renovacion") String refreshToken) {}
