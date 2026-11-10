package com.yachiqo.spotgo.profiles.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.DriverClassificationResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/driver-classifications", produces = "application/json")
@Tag(name = "Driver classifications", description = "TAXI, EMPLOYEE, or PRIVATE classification within a tenant. These are not access roles. assignedBy identifies the administrator's profile.")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "A valid accessToken and an active session are required.", content = @Content),
    @ApiResponse(responseCode = "400", description = "Invalid UUID or JSON, unknown profileType, or target profile is not a driver.", content = @Content),
    @ApiResponse(responseCode = "403", description = "The role does not allow this action, or the administrator is not assigned to the requested tenant.", content = @Content),
    @ApiResponse(responseCode = "404", description = "The caller's or target profile, driver account, or tenant does not exist.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Integrity conflict: only one classification is allowed per profile and tenant.", content = @Content),
    @ApiResponse(responseCode = "500", description = "Unexpected backend error. Check the server logs.", content = @Content)
})
public class DriverClassificationsController {
    private final ProfilesManagementService service;
    public DriverClassificationsController(ProfilesManagementService service) { this.service = service; }
    public record AssignClassificationResource(
        @Schema(description = "Classification for this tenant. Omit the field or send null to assign PRIVATE.", example = "EMPLOYEE") DriverProfileType profileType) {}

    @PutMapping("/tenants/{tenantId}/profiles/{profileId}")
    @Operation(summary = "Assign or update a driver classification", description = "Available only to the PARKING_ADMIN assigned to the tenant. Use the driver's profile UUID and the parking facility UUID as tenantId. The backend gets assignedBy from the access token; do not send it. Creates or updates one row per profile and tenant.")
    @ApiResponse(responseCode = "200", description = "Driver classification created or updated. An empty JSON object assigns PRIVATE.")
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    public DriverClassificationResource assign(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @Parameter(description = "UUID of the managed parking facility.", example = "00000000-0000-4000-8000-000000000021") @PathVariable UUID tenantId,
            @Parameter(description = "UUID of the driver's profile, not the account.", example = "00000000-0000-4000-8000-000000000012") @PathVariable UUID profileId,
            @RequestBody AssignClassificationResource resource) {
        return DriverClassificationResource.from(service.assign(actor, tenantId, profileId, resource.profileType()));
    }
    @GetMapping("/tenants/{tenantId}")
    @Operation(summary = "List driver classifications for my tenant", description = "Available only to the PARKING_ADMIN assigned to the specified tenant.")
    @ApiResponse(responseCode = "200", description = "Explicit classifications for this tenant; the list may be empty.")
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    public List<DriverClassificationResource> tenant(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @Parameter(description = "UUID of the managed parking facility.", example = "00000000-0000-4000-8000-000000000021") @PathVariable UUID tenantId) {
        return service.tenantClassifications(actor, tenantId).stream().map(DriverClassificationResource::from).toList();
    }
    @GetMapping("/me")
    @Operation(summary = "View my driver classifications", description = "Available to DRIVER only. Returns classifications for the authenticated driver across tenants.")
    @ApiResponse(responseCode = "200", description = "Classifications for the authenticated driver; the list may be empty.")
    @PreAuthorize("hasRole('DRIVER')")
    public List<DriverClassificationResource> mine(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return service.myClassifications(actor).stream().map(DriverClassificationResource::from).toList();
    }
}
