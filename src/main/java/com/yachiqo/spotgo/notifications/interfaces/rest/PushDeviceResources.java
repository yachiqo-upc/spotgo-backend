package com.yachiqo.spotgo.notifications.interfaces.rest;

import com.yachiqo.spotgo.notifications.domain.model.DevicePlatform;
import com.yachiqo.spotgo.notifications.domain.model.PushDeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PushDeviceResources {
    private PushDeviceResources() {}

    public record RegistrationRequest(
            @NotBlank @Size(max = 4096) String token,
            @NotNull DevicePlatform platform) {}

    public record DeviceResource(UUID deviceTokenId, DevicePlatform platform, Instant createdAt, Instant lastSeenAt) {
        public static DeviceResource from(PushDeviceToken registration) {
            return new DeviceResource(registration.getId(), registration.getPlatform(),
                    registration.getCreatedAt(), registration.getLastSeenAt());
        }
    }
}
