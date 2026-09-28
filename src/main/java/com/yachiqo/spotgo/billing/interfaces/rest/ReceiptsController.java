package com.yachiqo.spotgo.billing.interfaces.rest;

import com.yachiqo.spotgo.billing.application.commandservices.ReceiptCommandService;
import com.yachiqo.spotgo.billing.application.queryservices.ReceiptQueryService;
import com.yachiqo.spotgo.billing.domain.model.queries.GetReceiptByIdQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetReceiptsByReservationIdQuery;
import com.yachiqo.spotgo.billing.domain.repositories.ReceiptRepository;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.CreateReceiptResource;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.ReceiptResource;
import com.yachiqo.spotgo.billing.interfaces.rest.transform.ReceiptResourceFromEntityAssembler;
import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.interfaces.rest.resource.ErrorResource;
import com.yachiqo.spotgo.shared.interfaces.rest.transform.ErrorResponseAssembler;
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
@RequestMapping(value = "/api/v1/receipts", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CLIENT')")
@Tag(name = "Receipts", description = "Endpoints for managing parking receipts")
public class ReceiptsController {

    private final ReceiptCommandService receiptCommandService;
    private final ReceiptQueryService receiptQueryService;
    private final ReceiptRepository receiptRepository;

    public ReceiptsController(ReceiptCommandService receiptCommandService,
                              ReceiptQueryService receiptQueryService,
                              ReceiptRepository receiptRepository) {
        this.receiptCommandService = receiptCommandService;
        this.receiptQueryService = receiptQueryService;
        this.receiptRepository = receiptRepository;
    }

    @PostMapping
    @Operation(summary = "Create a receipt", description = "Receipts are generated automatically when a reservation is created.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Receipt created successfully",
                    content = @Content(schema = @Schema(implementation = ReceiptResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<?> createReceipt(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                           @Valid @RequestBody CreateReceiptResource resource) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResource("METHOD_NOT_ALLOWED", "Receipts are generated automatically when a reservation is created."));
    }

    @GetMapping
    @Operation(summary = "Get all receipts",
            description = "Returns all receipts. Optional query param: bookingCode to filter by booking code.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receipts retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ReceiptResource.class)))
    })
    public ResponseEntity<List<ReceiptResource>> getAllReceipts(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @RequestParam(required = false)
            @Parameter(description = "Filter receipts by reservation ID")
            String reservationId
    ) {
        var receipts = (reservationId != null && !reservationId.isBlank())
                ? receiptQueryService.handle(new GetReceiptsByReservationIdQuery(reservationId)).stream()
                    .filter(receipt -> principal.getUserId().equals(receipt.getClientId()))
                    .toList()
                : receiptRepository.findAllByClientId(principal.getUserId());
        var result = receipts.stream()
                .map(ReceiptResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{receiptId}")
    @Operation(summary = "Get receipt by ID", description = "Returns a specific receipt by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receipt found",
                    content = @Content(schema = @Schema(implementation = ReceiptResource.class))),
            @ApiResponse(responseCode = "404", description = "Receipt not found")
    })
    public ResponseEntity<?> getReceiptById(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @PathVariable
            @Parameter(description = "Receipt unique identifier", example = "1", required = true)
            String receiptId
    ) {
        var query = new GetReceiptByIdQuery(receiptId);
        var receipt = receiptQueryService.handle(query);
        if (receipt.isEmpty()) {
            var error = ApplicationError.notFound("Receipt", "Receipt with ID %s not found".formatted(receiptId));
            return ErrorResponseAssembler.toErrorResponseFromApplicationError(error);
        }
        if (!principal.getUserId().equals(receipt.get().getClientId())) {
            throw new AccessDeniedException("Receipt is outside authenticated scope");
        }
        return ResponseEntity.ok(ReceiptResourceFromEntityAssembler.toResourceFromEntity(receipt.get()));
    }

    @DeleteMapping("/{receiptId}")
    @Operation(summary = "Delete a receipt", description = "Deletes a receipt by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Receipt deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Receipt not found")
    })
    public ResponseEntity<?> deleteReceipt(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @PathVariable
            @Parameter(description = "Receipt unique identifier", example = "1", required = true)
            String receiptId
    ) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResource("METHOD_NOT_ALLOWED", "Receipts cannot be deleted manually."));
    }
}
