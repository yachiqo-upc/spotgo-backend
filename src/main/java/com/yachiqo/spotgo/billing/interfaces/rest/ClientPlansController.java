package com.yachiqo.spotgo.billing.interfaces.rest;

import com.yachiqo.spotgo.billing.application.queryservices.ClientPlanQueryService;
import com.yachiqo.spotgo.billing.domain.model.queries.GetAllClientPlansQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetClientPlanByIdQuery;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.ClientPlanResource;
import com.yachiqo.spotgo.billing.interfaces.rest.transform.ClientPlanResourceFromEntityAssembler;
import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.interfaces.rest.transform.ErrorResponseAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/clientPlans", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Client Plans", description = "Endpoints for retrieving available subscription plans")
public class ClientPlansController {

    private final ClientPlanQueryService clientPlanQueryService;

    public ClientPlansController(ClientPlanQueryService clientPlanQueryService) {
        this.clientPlanQueryService = clientPlanQueryService;
    }

    @GetMapping
    @Operation(summary = "Get all client plans", description = "Returns the full catalog of available subscription plans.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plans retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ClientPlanResource.class)))
    })
    public ResponseEntity<List<ClientPlanResource>> getAllClientPlans() {
        var plans = clientPlanQueryService.handle(new GetAllClientPlansQuery());
        if (plans.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        var resources = plans.stream()
                .map(ClientPlanResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{clientPlanId}")
    @Operation(summary = "Get client plan by ID", description = "Returns a specific subscription plan by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan found",
                    content = @Content(schema = @Schema(implementation = ClientPlanResource.class))),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public ResponseEntity<?> getClientPlanById(
            @PathVariable
            @Parameter(description = "Client plan unique identifier", example = "1", required = true)
            String clientPlanId
    ) {
        var query = new GetClientPlanByIdQuery(clientPlanId);
        var plan = clientPlanQueryService.handle(query);
        if (plan.isEmpty()) {
            var error = ApplicationError.notFound("ClientPlan", "ClientPlan with ID %s not found".formatted(clientPlanId));
            return ErrorResponseAssembler.toErrorResponseFromApplicationError(error);
        }
        return ResponseEntity.ok(ClientPlanResourceFromEntityAssembler.toResourceFromEntity(plan.get()));
    }
}
