package com.yachiqo.spotgo.iam.interfaces.rest;

import com.yachiqo.spotgo.iam.application.AuthenticationService;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/authentication", produces = "application/json")
@Tag(name = "Authentication", description = "Accounts, PASSWORD credentials, and UUID-based sessions. Swagger Authorize uses accessToken; refreshToken is sent only to /refresh.")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid JSON or password. Passwords must contain at least 8 characters, including an uppercase letter, a lowercase letter, a number, and a symbol.", content = @Content),
    @ApiResponse(responseCode = "401", description = "Credentials, token, or session are invalid, expired, or revoked, or the account is disabled.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Duplicate email address or integrity conflict.", content = @Content),
    @ApiResponse(responseCode = "429", description = "Too many attempts. Wait for Retry-After before trying again.", content = @Content),
    @ApiResponse(responseCode = "500", description = "Unexpected server error.", content = @Content)
})
public class AuthenticationController {
    private final AuthenticationService service;
    public AuthenticationController(AuthenticationService service) { this.service = service; }

    @PostMapping("/sign-in")
    @SecurityRequirements
    @Operation(summary = "Sign in", description = "Verifies credentials.secret_hash, creates a session, and returns accessToken and refreshToken. Paste only accessToken into Swagger Authorize. Passwords and tokens are never stored in plain text.")
    @ApiResponse(responseCode = "200", description = "Session created. Store both tokens and their expiration times.")
    public AuthenticatedUserResource signIn(@Valid @RequestBody SignInResource body, HttpServletRequest request) {
        return service.signIn(body.email(), body.password(), request.getRemoteAddr());
    }

    @PostMapping("/sign-up")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a DRIVER account", description = "Creates an account, a PASSWORD credential, a DRIVER role assignment, and a session. Personal profile data is created separately with POST /profiles. Privileged roles cannot be selected.")
    @ApiResponse(responseCode = "201", description = "DRIVER account and session created; the profile may not exist yet.")
    public AuthenticatedUserResource signUp(@Valid @RequestBody SignUpResource body, HttpServletRequest request) {
        return service.signUp(body.email(), body.password(), request.getRemoteAddr());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Refresh access and refresh tokens", description = "Accepts refreshToken without a valid access token. Keeps the sessionId and original session expiration, issues a new refreshToken, and invalidates the previous one. Do not reuse the old token.")
    @ApiResponse(responseCode = "200", description = "New tokens issued. Replace both tokens in the client.")
    public AuthenticatedUserResource refresh(@Valid @RequestBody RefreshTokenResource body, HttpServletRequest request) {
        return service.refresh(body.refreshToken(), request.getRemoteAddr());
    }

    @PostMapping("/sign-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out of the current session", description = "Requires the accessToken in Swagger Authorize. Revokes the token's session so neither its access token nor refresh token is accepted.")
    @ApiResponse(responseCode = "204", description = "Session revoked; no response body.", content = @Content)
    public void signOut(@AuthenticationPrincipal SpotgoUserPrincipal actor, HttpServletRequest request) {
        service.signOut(actor, request.getRemoteAddr());
    }

    @PostMapping("/password-reset/request")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Request a password recovery code", description = "Requires Resend configuration. Returns the same response for known and unknown email addresses. The one-time code is emailed, and only its hash is stored in the existing password recovery table.")
    @ApiResponse(responseCode = "204", description = "Request received; this does not confirm whether the email address exists.", content = @Content)
    @ApiResponse(responseCode = "503", description = "Email delivery is not configured or is unavailable.", content = @Content)
    public void requestReset(@Valid @RequestBody PasswordResetRequestResource body, HttpServletRequest request) {
        service.requestPasswordReset(body.email(), request.getRemoteAddr());
    }

    @PostMapping("/password-reset/confirm")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm sensor recovery", description = "Validates the code, expiration, attempt limit, and one-time use. Updates the password hash and revokes every session for the account.")
    @ApiResponse(responseCode = "204", description = "Password changed. Sign in again.", content = @Content)
    public void confirmReset(@Valid @RequestBody PasswordResetConfirmResource body, HttpServletRequest request) {
        service.confirmPasswordReset(body.email(), body.code(), body.newPassword(), request.getRemoteAddr());
    }
}
