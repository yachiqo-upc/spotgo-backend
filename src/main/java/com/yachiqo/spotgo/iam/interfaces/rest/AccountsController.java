package com.yachiqo.spotgo.iam.interfaces.rest;

import com.yachiqo.spotgo.iam.application.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.*;
import com.yachiqo.spotgo.profiles.interfaces.rest.resources.ProfileResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/accounts", produces = "application/json")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid UUID, JSON, status, role, dates, or tenant scope.", content = @Content),
    @ApiResponse(responseCode = "401", description = "A valid accessToken and an active session are required.", content = @Content),
    @ApiResponse(responseCode = "403", description = "This action requires SUPER_ADMIN or access to the caller's own resource.", content = @Content),
    @ApiResponse(responseCode = "404", description = "Account or role assignment not found.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Integrity conflict: an account can have only one role assignment.", content = @Content)
})
public class AccountsController {
    private final AccountAdministrationService administration;
    private final AuthenticationService authentication;
    public AccountsController(AccountAdministrationService administration, AuthenticationService authentication) {
        this.administration = administration; this.authentication = authentication;
    }
    public record ChangeStatusResource(@NotNull AccountStatus status) {}
    public record AssignRoleResource(@NotNull UserRole roleType,
        @Schema(description = "Required for PARKING_ADMIN; NULL for DRIVER or SUPER_ADMIN.") UUID tenantId,
        @Schema(description = "Omit the field to use the current timestamp.") Instant validFrom,
        @Schema(description = "NULL means there is no end date; it does not mean the status is ACTIVE.") Instant validTo,
        AssignmentStatus status) {}
    public record ProvisionParkingAdminResource(@NotNull UUID tenantId, @NotBlank @Email String email,
            @NotBlank String firstName, @NotBlank String lastName, @NotBlank String phone) {}
    public record ProvisionedParkingAdminResource(AccountResource account, RoleAssignmentResource roleAssignment,
                                                   ProfileResource profile) {}

    @GetMapping("/me")
    @Operation(tags = "Accounts", summary = "View my account", description = "Returns the account associated with the access token, including its email and status. Retrieve the personal profile from /profiles/me.")
    @ApiResponse(responseCode = "200", description = "Authenticated account found.")
    public AccountResource mine(@AuthenticationPrincipal SpotgoUserPrincipal actor) { return AccountResource.from(administration.own(actor)); }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Accounts", summary = "List accounts", description = "Available to SUPER_ADMIN only. page starts at 0 and size must be between 1 and 100.")
    @ApiResponse(responseCode = "200", description = "Paginated accounts without passwords or tokens.")
    public Page<AccountResource> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return administration.list(actor, page, size).map(AccountResource::from);
    }
    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Accounts", summary = "Get an account", description = "Available to SUPER_ADMIN only. PARKING_ADMIN cannot browse all system accounts.")
    @ApiResponse(responseCode = "200", description = "Account found.")
    public AccountResource get(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID accountId) {
        return AccountResource.from(administration.get(actor, accountId));
    }
    @PatchMapping("/{accountId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Accounts", summary = "Update account status", description = "Available to SUPER_ADMIN only. Changing the status from ACTIVE revokes all sessions. Reactivating the account does not restore its previous sessions.")
    @ApiResponse(responseCode = "200", description = "Status updated.")
    public AccountResource status(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID accountId,
            @Valid @RequestBody ChangeStatusResource body, HttpServletRequest request) {
        return AccountResource.from(administration.status(actor, accountId, body.status(), request.getRemoteAddr()));
    }
    @GetMapping("/me/role")
    @Operation(tags = "Role assignments", summary = "View my role assignment", description = "effective evaluates status and the assignment dates; a NULL validTo alone does not make a role effective.")
    @ApiResponse(responseCode = "200", description = "Current account's role assignment.")
    public RoleAssignmentResource ownRole(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return RoleAssignmentResource.from(administration.role(actor, actor.getAccountId()), administration.now());
    }
    @GetMapping("/{accountId}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Role assignments", summary = "View an account's role assignment", description = "Available to SUPER_ADMIN only.")
    @ApiResponse(responseCode = "200", description = "Role assignment found.")
    public RoleAssignmentResource role(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID accountId) {
        return RoleAssignmentResource.from(administration.role(actor, accountId), administration.now());
    }
    @PutMapping("/{accountId}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(tags = "Role assignments", summary = "Assign or update an account role", description = "Available to SUPER_ADMIN only. Creates or updates the single role-assignment row allowed by the unique account_id. PARKING_ADMIN requires an existing tenant. The current assignment is checked on every request, even if the JWT contains an older role claim.")
    @ApiResponse(responseCode = "200", description = "Role assignment created or updated.")
    public RoleAssignmentResource assign(@AuthenticationPrincipal SpotgoUserPrincipal actor, @PathVariable UUID accountId,
            @Valid @RequestBody AssignRoleResource body, HttpServletRequest request) {
        return RoleAssignmentResource.from(administration.assign(actor, accountId, body.roleType(), body.tenantId(),
            body.validFrom(), body.validTo(), body.status(), request.getRemoteAddr()), administration.now());
    }
    @PostMapping("/parking-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(tags = "Accounts", summary = "Provision a Parking Admin", description = "Available to SUPER_ADMIN only. Creates a PASSWORD account, one PARKING_ADMIN assignment scoped to an active tenant, and a personal profile. Sends a one-time account setup code by email; RESEND_API_KEY and APP_PASSWORD_RESET_FROM_EMAIL must be configured. The temporary password is never returned.")
    @ApiResponse(responseCode = "201", description = "Parking Admin account, role assignment, profile, and invitation created.")
    @ApiResponse(responseCode = "503", description = "The invitation email could not be sent; the account creation is rolled back.", content = @Content)
    public ProvisionedParkingAdminResource provisionParkingAdmin(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @Valid @RequestBody ProvisionParkingAdminResource body, HttpServletRequest request) {
        var created = administration.provisionParkingAdmin(actor, body.tenantId(), body.email(), body.firstName(),
                body.lastName(), body.phone(), request.getRemoteAddr());
        return new ProvisionedParkingAdminResource(AccountResource.from(created.account()),
                RoleAssignmentResource.from(created.roleAssignment(), administration.now()),
                ProfileResource.from(created.profile()));
    }
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(tags = "Accounts", summary = "Change my password", description = "Validates the current password. The new password must be different and meet the password policy. Revokes all sessions, including this one; sign in again.")
    @ApiResponse(responseCode = "204", description = "Password changed; no response body.", content = @Content)
    public void password(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @Valid @RequestBody UpdatePasswordResource body, HttpServletRequest request) {
        authentication.updatePassword(actor, body.currentPassword(), body.newPassword(), request.getRemoteAddr());
    }
}
