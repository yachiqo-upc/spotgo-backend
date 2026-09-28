package com.yachiqo.spotgo.iam.interfaces.rest.resources;

public record UserResource(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String role,
        String parkingId,
        String parkingName
) {
}
