package com.yachiqo.spotgo.profiles.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/profiles", produces = "application/json")
@Tag(name = "Profiles", description = "Personal profiles for the authenticated account. Sign in and provide the access token through Swagger Authorize first.")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "A valid accessToken is required, the account is inactive, or the session has expired or been revoked. Sign in and use Swagger Authorize.", content = @Content),
    @ApiResponse(responseCode = "400", description = "Invalid JSON or empty first name, last name, or phone number.", content = @Content),
    @ApiResponse(responseCode = "403", description = "The authenticated account is not allowed to perform this action.", content = @Content),
    @ApiResponse(responseCode = "404", description = "The requested account or profile does not exist. If the profile is missing, use POST /api/v1/profiles.", content = @Content),
    @ApiResponse(responseCode = "409", description = "The account already has a profile or an integrity conflict occurred.", content = @Content),
    @ApiResponse(responseCode = "500", description = "Unexpected backend error. Check the server logs.", content = @Content)
})
public class ProfilesController {
    private final ProfilesManagementService service;
    public ProfilesController(ProfilesManagementService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Create my profile", description = "Creates a profile for the account in the access token. Do not send accountId, id, or timestamps; the backend resolves or generates them. If the demo seeder already created the profile, this request returns 409; use GET /me to retrieve it.")
    @ApiResponse(responseCode = "201", description = "Profile created with a generated UUID and timestamps.")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResource create(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                  @Valid @RequestBody SaveProfileResource resource) {
        return ProfileResource.from(service.create(actor, resource.firstName(), resource.lastName(), resource.phone()));
    }
    @GetMapping("/me")
    @Operation(summary = "View my profile", description = "Returns the profile for the signed-in account. No UUID is required because the Bearer token identifies the account.")
    @ApiResponse(responseCode = "200", description = "Authenticated profile found.")
    public ProfileResource current(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return ProfileResource.from(service.current(actor));
    }
    @PutMapping("/me")
    @Operation(summary = "Update my profile", description = "Requires first name, last name, and phone. Keeps the account and createdAt unchanged; updates updatedAt when profile data changes.")
    @ApiResponse(responseCode = "200", description = "Authenticated profile updated.")
    public ProfileResource update(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                  @Valid @RequestBody SaveProfileResource resource) {
        return ProfileResource.from(service.update(actor, resource.firstName(), resource.lastName(), resource.phone()));
    }
}
