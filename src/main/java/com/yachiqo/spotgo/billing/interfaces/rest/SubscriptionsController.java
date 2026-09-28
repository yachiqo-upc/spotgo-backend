package com.yachiqo.spotgo.billing.interfaces.rest;

import com.yachiqo.spotgo.billing.application.commandservices.SubscriptionCommandService;
import com.yachiqo.spotgo.billing.application.queryservices.SubscriptionQueryService;
import com.yachiqo.spotgo.billing.domain.model.commands.CreateSubscriptionCommand;
import com.yachiqo.spotgo.billing.domain.repositories.SubscriptionRepository;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.billing.domain.model.queries.GetSubscriptionByIdQuery;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.CreateSubscriptionResource;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.SubscriptionResource;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.UpdateSubscriptionResource;
import com.yachiqo.spotgo.billing.interfaces.rest.transform.SubscriptionResourceFromEntityAssembler;
import com.yachiqo.spotgo.billing.interfaces.rest.transform.UpdateSubscriptionCommandFromResourceAssembler;
import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.interfaces.rest.transform.ErrorResponseAssembler;
import com.yachiqo.spotgo.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Subscriptions", description = "Endpoints for managing client subscriptions")
public class SubscriptionsController {

    private final SubscriptionCommandService subscriptionCommandService;
    private final SubscriptionQueryService subscriptionQueryService;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionsController(SubscriptionCommandService subscriptionCommandService,
                                   SubscriptionQueryService subscriptionQueryService,
                                   SubscriptionRepository subscriptionRepository) {
        this.subscriptionCommandService = subscriptionCommandService;
        this.subscriptionQueryService = subscriptionQueryService;
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostMapping
    @Operation(summary = "Create a subscription", description = "Creates a new subscription for a client.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Subscription created successfully",
                    content = @Content(schema = @Schema(implementation = SubscriptionResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<?> createSubscription(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                @Valid @RequestBody CreateSubscriptionResource resource) {
        var command = new CreateSubscriptionCommand(
                principal.getUserId(),
                resource.planId(),
                resource.autoRenewal(),
                resource.paymentMethodLastFour(),
                resource.paymentMethodExpiry());
        var result = subscriptionCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                SubscriptionResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED
        );
    }

    @GetMapping
    @Operation(summary = "Get all subscriptions", description = "Returns all subscriptions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriptions retrieved successfully",
                    content = @Content(schema = @Schema(implementation = SubscriptionResource.class)))
    })
    public ResponseEntity<List<SubscriptionResource>> getAllSubscriptions(@AuthenticationPrincipal SpotgoUserPrincipal principal) {
        var subscriptions = subscriptionRepository.findAllByClientId(principal.getUserId());
        if (subscriptions.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        var resources = subscriptions.stream()
                .map(SubscriptionResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Get subscription by ID", description = "Returns a specific subscription by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription found",
                    content = @Content(schema = @Schema(implementation = SubscriptionResource.class))),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
    public ResponseEntity<?> getSubscriptionById(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @PathVariable
            @Parameter(description = "Subscription unique identifier", example = "1", required = true)
            String subscriptionId
    ) {
        var query = new GetSubscriptionByIdQuery(subscriptionId);
        var subscription = subscriptionQueryService.handle(query);
        if (subscription.isEmpty()) {
            var error = ApplicationError.notFound("Subscription", "Subscription with ID %s not found".formatted(subscriptionId));
            return ErrorResponseAssembler.toErrorResponseFromApplicationError(error);
        }
        if (!principal.getUserId().equals(subscription.get().getClientId())) {
            throw new AccessDeniedException("Subscription is outside authenticated scope");
        }
        return ResponseEntity.ok(SubscriptionResourceFromEntityAssembler.toResourceFromEntity(subscription.get()));
    }

    @PutMapping("/{subscriptionId}")
    @Operation(summary = "Update a subscription",
            description = "Updates a subscription. Used for switching plans, toggling auto-renewal, or updating payment method.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription updated successfully",
                    content = @Content(schema = @Schema(implementation = SubscriptionResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
    public ResponseEntity<?> updateSubscription(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @PathVariable
            @Parameter(description = "Subscription unique identifier", example = "1", required = true)
            String subscriptionId,
            @Valid @RequestBody UpdateSubscriptionResource resource
    ) {
        requireSubscriptionOwner(principal, subscriptionId);
        var command = UpdateSubscriptionCommandFromResourceAssembler.toCommandFromResource(subscriptionId, resource);
        var result = subscriptionCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                SubscriptionResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK
        );
    }

    @PatchMapping("/{subscriptionId}")
    @Operation(summary = "Update a subscription (PATCH)",
            description = "Partially updates a subscription. Used by the frontend for switching plans, toggling auto-renewal, or updating payment method.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription updated successfully",
                    content = @Content(schema = @Schema(implementation = SubscriptionResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
    public ResponseEntity<?> patchSubscription(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @PathVariable
            @Parameter(description = "Subscription unique identifier", example = "1", required = true)
            String subscriptionId,
            @Valid @RequestBody UpdateSubscriptionResource resource
    ) {
        requireSubscriptionOwner(principal, subscriptionId);
        var command = UpdateSubscriptionCommandFromResourceAssembler.toCommandFromResource(subscriptionId, resource);
        var result = subscriptionCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                SubscriptionResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK
        );
    }

    private void requireSubscriptionOwner(SpotgoUserPrincipal principal, String subscriptionId) {
        var subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new AccessDeniedException("Subscription does not exist"));
        if (!principal.getUserId().equals(subscription.getClientId())) {
            throw new AccessDeniedException("Subscription is outside authenticated scope");
        }
    }
}
