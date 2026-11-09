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
@Tag(name = "Profiles", description = "Perfiles personales de la cuenta autenticada. Primero inicia sesion y pega el token en Authorize.")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "JSON invalido o nombres, apellidos o telefono vacios.", content = @Content),
    @ApiResponse(responseCode = "403", description = "Acceso rechazado: falta el token Bearer, es invalido o no tienes permiso. Usa sign-in y Authorize.", content = @Content),
    @ApiResponse(responseCode = "404", description = "La cuenta o el perfil solicitado no existe. Si falta el perfil, utiliza POST /api/v1/profiles.", content = @Content),
    @ApiResponse(responseCode = "409", description = "La cuenta ya tiene perfil, existe un conflicto de integridad o la cuenta antigua requiere migracion a UUID.", content = @Content),
    @ApiResponse(responseCode = "500", description = "Error inesperado del backend. Revisar la consola del servidor.", content = @Content)
})
public class ProfilesController {
    private final ProfilesManagementService service;
    public ProfilesController(ProfilesManagementService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Crear mi perfil", description = "Crea un perfil para la cuenta del token. No envies accountId, id ni fechas: el backend los obtiene o genera. Con el seeder demo el perfil ya existe y este POST responde 409; utiliza GET /me para consultarlo.")
    @ApiResponse(responseCode = "201", description = "Perfil creado con UUID y fechas automaticas.")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResource create(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                  @Valid @RequestBody SaveProfileResource resource) {
        return ProfileResource.from(service.create(actor, resource.firstName(), resource.lastName(), resource.phone()));
    }
    @GetMapping("/me")
    @Operation(summary = "Consultar mi perfil", description = "Devuelve el perfil de quien inicio sesion. No recibe un UUID porque identifica la cuenta mediante el token Bearer.")
    @ApiResponse(responseCode = "200", description = "Perfil propio encontrado.")
    public ProfileResource current(@AuthenticationPrincipal SpotgoUserPrincipal actor) {
        return ProfileResource.from(service.current(actor));
    }
    @PutMapping("/me")
    @Operation(summary = "Actualizar mi perfil", description = "Requiere nombres, apellidos y telefono. Conserva la cuenta y createdAt; actualiza updatedAt cuando cambian los datos.")
    @ApiResponse(responseCode = "200", description = "Perfil propio actualizado.")
    public ProfileResource update(@AuthenticationPrincipal SpotgoUserPrincipal actor,
                                  @Valid @RequestBody SaveProfileResource resource) {
        return ProfileResource.from(service.update(actor, resource.firstName(), resource.lastName(), resource.phone()));
    }
}
