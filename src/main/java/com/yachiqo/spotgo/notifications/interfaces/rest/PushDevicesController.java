package com.yachiqo.spotgo.notifications.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.notifications.application.PushDeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/notifications/devices", produces = "application/json")
@Tag(name = "Push Notifications", description = "Registers the signed-in user's Firebase device tokens for event-driven push notifications.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "A valid accessToken is required.", content = @io.swagger.v3.oas.annotations.media.Content),
        @ApiResponse(responseCode = "500", description = "Unexpected server error.", content = @io.swagger.v3.oas.annotations.media.Content)
})
public class PushDevicesController {
    private final PushDeviceTokenService service;

    public PushDevicesController(PushDeviceTokenService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List my registered devices", description = "Returns registration metadata for the signed-in account without exposing Firebase device tokens.")
    @ApiResponse(responseCode = "200", description = "The current account's registered devices.")
    public List<PushDeviceResources.DeviceResource> ownDevices(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return service.ownDevices(actor).stream().map(PushDeviceResources.DeviceResource::from).toList();
    }

    @PostMapping(consumes = "application/json")
    @Operation(summary = "Register or refresh a device token", description = "Stores the Firebase registration token for this authenticated account. Re-registering a token updates its owner and last-seen time. The raw token is never returned.")
    @ApiResponse(responseCode = "200", description = "The device token is registered.")
    @ApiResponse(responseCode = "400", description = "The token or platform is invalid.", content = @io.swagger.v3.oas.annotations.media.Content)
    public PushDeviceResources.DeviceResource register(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                                       @Valid @RequestBody PushDeviceResources.RegistrationRequest body) {
        return PushDeviceResources.DeviceResource.from(service.register(actor, body.token(), body.platform()));
    }

    @DeleteMapping("/{deviceTokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove one of my devices", description = "Deletes the device registration only when it belongs to the signed-in account.")
    @ApiResponse(responseCode = "204", description = "Device registration deleted.", content = @io.swagger.v3.oas.annotations.media.Content)
    @ApiResponse(responseCode = "404", description = "The device registration does not belong to the signed-in account or does not exist.", content = @io.swagger.v3.oas.annotations.media.Content)
    public void remove(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID deviceTokenId) {
        service.remove(actor, deviceTokenId);
    }
}
