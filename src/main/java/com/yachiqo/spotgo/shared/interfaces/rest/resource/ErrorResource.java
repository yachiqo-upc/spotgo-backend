package com.yachiqo.spotgo.shared.interfaces.rest.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResource(
        String code,
        String message,
        @Nullable String details
) {

    public ErrorResource(String code, String message) {
        this(code, message, null);
    }
}
