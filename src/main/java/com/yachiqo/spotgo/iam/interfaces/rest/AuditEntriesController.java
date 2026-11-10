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
@Tag(name = "Audit entries", description = "Historial de eventos de identidad y seguridad, no de todas las acciones del negocio. No guarda contraseñas, codigos ni tokens.")
public class AuditEntriesController {
    private final AccountAdministrationService service;
    public AuditEntriesController(AccountAdministrationService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Consultar auditoria", description = "Solo SUPER_ADMIN. accountId opcional filtra por cuenta. accountId NULL en una fila significa que no se identifico una cuenta. page >= 0, size entre 1 y 100.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagina de eventos de seguridad."),
        @ApiResponse(responseCode = "400", description = "UUID o paginacion invalidos.", content = @Content),
        @ApiResponse(responseCode = "401", description = "Access token ausente o invalido.", content = @Content),
        @ApiResponse(responseCode = "403", description = "Se requiere SUPER_ADMIN.", content = @Content)
    })
    public Page<AuditEntryResource> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,
            @RequestParam(required = false) UUID accountId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.audit(actor, accountId, page, size).map(AuditEntryResource::from);
    }
}
