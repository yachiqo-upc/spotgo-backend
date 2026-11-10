package com.yachiqo.spotgo.iam.interfaces.rest;
import com.yachiqo.spotgo.iam.application.AccountAdministrationService;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.AuditEntryResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping(value = "/api/v1/audit-entries", produces = "application/json")
@Tag(name = "Audit entries", description = "Identity and security events, not a log of every business action. Passwords, codes, and tokens are never stored.")
public class AuditEntriesController {
    private final AccountAdministrationService service;
    public AuditEntriesController(AccountAdministrationService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "View audit events", description = "Available to SUPER_ADMIN only. accountId is an optional account filter. A NULL accountId means the event could not be associated with an account. page must be at least 0 and size must be between 1 and 100.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated security events."),
        @ApiResponse(responseCode = "400", description = "Invalid UUID or pagination parameters.", content = @Content),
        @ApiResponse(responseCode = "401", description = "The access token is missing or invalid.", content = @Content),
        @ApiResponse(responseCode = "403", description = "SUPER_ADMIN is required.", content = @Content)
    })
    public Page<AuditEntryResource> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @RequestParam(required = false) UUID accountId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.audit(actor, accountId, page, size).map(AuditEntryResource::from);
    }
}
