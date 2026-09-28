package com.yachiqo.spotgo.iam.interfaces.rest;

import com.yachiqo.spotgo.iam.application.UserAccountService;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.PasswordResetConfirmResource;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.PasswordResetRequestResource;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.SignInResource;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.SignUpResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authentication")
@Tag(name = "Authentication", description = "Endpoints for sign in, sign up, and password reset")
public class AuthenticationController {

    private final UserAccountService userAccountService;

    public AuthenticationController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping("/sign-in")
    @Operation(summary = "Sign in", description = "Authenticates a user with email and password.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    public ResponseEntity<?> signIn(@Valid @RequestBody SignInResource request) {
        return ResponseEntity.ok(userAccountService.signIn(request.email(), request.password()));
    }

    @PostMapping("/sign-up")
    @Operation(summary = "Sign up", description = "Registers a new client account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpResource request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userAccountService.signUpClient(request.firstName(), request.lastName(), request.phone(), request.email(), request.password()));
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request password reset", description = "Requests a one-time reset code to be sent to the provided email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Password reset request accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestResource request) {
        userAccountService.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Confirm password reset", description = "Confirms a password reset with email, one-time code, and new password.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or reset code", content = @Content)
    })
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmResource request) {
        userAccountService.confirmPasswordReset(request.email(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
