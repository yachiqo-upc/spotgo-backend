package com.yachiqo.spotgo.parking.interfaces.rest;

import com.yachiqo.spotgo.parking.domain.model.aggregates.ClientReport;
import com.yachiqo.spotgo.parking.domain.model.commands.CreateClientReportCommand;
import com.yachiqo.spotgo.parking.domain.model.commands.UpdateClientReportStatusCommand;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReportStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReportType;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandService;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryService;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ClientReportRepository;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.ClientReportResource;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.CreateClientReportResource;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.UpdateClientReportStatusResource;
import com.yachiqo.spotgo.shared.application.security.AuthorizationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clientReports")
@Tag(name = "ClientReports", description = "Endpoints for managing client-submitted parking reports")
public class ClientReportsController {

    private final ParkingCommandService parkingCommandService;
    private final ParkingQueryService parkingQueryService;
    private final ClientReportRepository clientReportRepository;
    private final AuthorizationService authorizationService;

    public ClientReportsController(ParkingCommandService parkingCommandService, ParkingQueryService parkingQueryService,
                                   ClientReportRepository clientReportRepository, AuthorizationService authorizationService) {
        this.parkingCommandService = parkingCommandService;
        this.parkingQueryService = parkingQueryService;
        this.clientReportRepository = clientReportRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Create a client report", description = "Submits a new report from a client about a past reservation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Report created successfully",
                    content = @Content(schema = @Schema(implementation = ClientReportResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<ClientReportResource> createClientReport(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                                   @Valid @RequestBody CreateClientReportResource resource) {
        var command = new CreateClientReportCommand(
                principal.getUserId(),
                resource.parkingId(),
                resource.reservationId(),
                ReportType.fromDisplayName(resource.type()),
                resource.date()
        );
        var reportOptional = parkingCommandService.handle(command);
        if (reportOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return new ResponseEntity<>(toResource(reportOptional.get()), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PARKING_ADMIN','DRIVER')")
    @Operation(summary = "Get all client reports", description = "Returns the full list of client reports.")
    @ApiResponse(responseCode = "200", description = "List of client reports returned",
            content = @Content(schema = @Schema(implementation = ClientReportResource.class)))
    public ResponseEntity<List<ClientReportResource>> getAllClientReports(@AuthenticationPrincipal SpotgoUserPrincipal principal) {
        List<ClientReport> reports;
        if (authorizationService.isAdmin(principal)) {
            var parkingId = authorizationService.requireAdminParkingId(principal);
            reports = clientReportRepository.findAll().stream()
                    .filter(report -> parkingId.equals(report.getParkingId()))
                    .toList();
        } else {
            reports = clientReportRepository.findAll().stream()
                    .filter(report -> principal.getUserId().equals(report.getClientId()))
                    .toList();
        }
        var resources = reports.stream()
                .map(this::toResource)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PatchMapping("/{reportId}")
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Operation(summary = "Update report status", description = "Updates the status of a client report (e.g. mark as resolved).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully",
                    content = @Content(schema = @Schema(implementation = ClientReportResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status value or report not found")
    })
    public ResponseEntity<ClientReportResource> updateClientReportStatus(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @PathVariable String reportId,
            @Valid @RequestBody UpdateClientReportStatusResource resource) {
        var currentReport = clientReportRepository.findById(reportId).orElse(null);
        if (currentReport == null) {
            return ResponseEntity.badRequest().build();
        }
        authorizationService.requireClientReportAccess(principal, currentReport);
        var command = new UpdateClientReportStatusCommand(reportId, ReportStatus.fromDisplayName(resource.status()));
        var reportOptional = parkingCommandService.handle(command);
        if (reportOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(toResource(reportOptional.get()));
    }

    private ClientReportResource toResource(ClientReport report) {
        return new ClientReportResource(
                report.getId(),
                report.getCode(),
                report.getClientId(),
                report.getParkingId(),
                report.getReservationId(),
                report.getType().getDisplayName(),
                report.getDate().toString(),
                report.getStatus().getDisplayName()
        );
    }
}
