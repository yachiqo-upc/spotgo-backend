package com.yachiqo.spotgo.monitoring.interfaces.rest;

import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.monitoring.application.EmployeeSpotAssignmentService;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.monitoring.domain.model.commands.CreateEmployeeCommand;
import com.yachiqo.spotgo.monitoring.domain.model.commands.DeleteEmployeeCommand;
import com.yachiqo.spotgo.monitoring.domain.model.commands.UpdateEmployeeCommand;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeRole;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeSchedule;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeStatus;
import com.yachiqo.spotgo.monitoring.application.internal.commandservices.MonitoringCommandService;
import com.yachiqo.spotgo.monitoring.application.internal.queryservices.MonitoringQueryService;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.CreateEmployeeResource;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.EmployeeResource;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.UpdateEmployeeResource;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees")
@PreAuthorize("hasRole('PARKING_ADMIN')")
@Tag(name = "Employees", description = "Endpoints for managing parking employees")
public class EmployeesController {

    private final MonitoringCommandService monitoringCommandService;
    private final MonitoringQueryService monitoringQueryService;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;
    private final EmployeeSpotAssignmentService employeeSpotAssignmentService;


    public EmployeesController(MonitoringCommandService monitoringCommandService, MonitoringQueryService monitoringQueryService,
                               EmployeeRepository employeeRepository, AuthorizationService authorizationService,
                               EmployeeSpotAssignmentService employeeSpotAssignmentService) {
        this.monitoringCommandService = monitoringCommandService;
        this.monitoringQueryService = monitoringQueryService;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
        this.employeeSpotAssignmentService = employeeSpotAssignmentService;

    }

    @PostMapping
    @Transactional
    @Operation(summary = "Create an employee", description = "Adds a new employee to a parking facility.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Employee created successfully",
                    content = @Content(schema = @Schema(implementation = EmployeeResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<EmployeeResource> createEmployee(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                                           @Valid @RequestBody CreateEmployeeResource resource) {
        var parkingId = authorizationService.requireAdminParkingId(principal);
        employeeSpotAssignmentService.validateAssignment(
                parkingId,
                null,
                resource.assignedSpot(),
                EmployeeSchedule.fromDisplayName(resource.schedule()),
                resource.shiftStart(),
                resource.shiftEnd(),
                EmployeeStatus.fromDisplayName(resource.status()));
        var command = new CreateEmployeeCommand(
                parkingId, resource.firstName(), resource.lastName(),
                EmployeeRole.fromDisplayName(resource.role()), EmployeeSchedule.fromDisplayName(resource.schedule()),
                resource.shiftStart(), resource.shiftEnd(),
                employeeSpotAssignmentService.normalizeSpot(resource.assignedSpot()),
                EmployeeStatus.fromDisplayName(resource.status()));
        var employeeOptional = monitoringCommandService.handle(command);
        if (employeeOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return new ResponseEntity<>(toResource(employeeOptional.get()), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all employees", description = "Returns the full list of employees.")
    @ApiResponse(responseCode = "200", description = "List of employees returned",
            content = @Content(schema = @Schema(implementation = EmployeeResource.class)))
    public ResponseEntity<List<EmployeeResource>> getAllEmployees(@AuthenticationPrincipal SpotgoUserPrincipal principal) {
        var parkingId = authorizationService.requireAdminParkingId(principal);
        var employees = employeeRepository.findByParkingId(parkingId);
        var resources = employees.stream().map(this::toResource).toList();
        return ResponseEntity.ok(resources);
    }

    @RequestMapping(value = "/{employeeId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @Transactional
    @Operation(summary = "Update an employee", description = "Replaces an employee's data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Employee updated successfully",
                    content = @Content(schema = @Schema(implementation = EmployeeResource.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or employee not found")
    })
    public ResponseEntity<EmployeeResource> updateEmployee(
            @AuthenticationPrincipal SpotgoUserPrincipal principal,
            @PathVariable String employeeId,
            @Valid @RequestBody UpdateEmployeeResource resource) {
        var currentEmployee = employeeRepository.findById(employeeId).orElse(null);
        if (currentEmployee == null) {
            return ResponseEntity.badRequest().build();
        }
        authorizationService.requireEmployeeOwnership(principal, currentEmployee);
        employeeSpotAssignmentService.validateAssignment(
                currentEmployee.getParkingId(),
                currentEmployee.getId(),
                resource.assignedSpot(),
                EmployeeSchedule.fromDisplayName(resource.schedule()),
                resource.shiftStart(),
                resource.shiftEnd(),
                EmployeeStatus.fromDisplayName(resource.status()));
        var command = new UpdateEmployeeCommand(
                employeeId, resource.firstName(), resource.lastName(),
                EmployeeRole.fromDisplayName(resource.role()), EmployeeSchedule.fromDisplayName(resource.schedule()),
                resource.shiftStart(), resource.shiftEnd(),
                employeeSpotAssignmentService.normalizeSpot(resource.assignedSpot()),
                EmployeeStatus.fromDisplayName(resource.status()));
        var employeeOptional = monitoringCommandService.handle(command);
        if (employeeOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(toResource(employeeOptional.get()));
    }

    @DeleteMapping("/{employeeId}")
    @Operation(summary = "Delete an employee", description = "Removes an employee by its ID.")
    @ApiResponse(responseCode = "204", description = "Employee deleted successfully")
    public ResponseEntity<Void> deleteEmployee(@AuthenticationPrincipal SpotgoUserPrincipal principal,
                                               @PathVariable String employeeId) {
        var currentEmployee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        authorizationService.requireEmployeeOwnership(principal, currentEmployee);
        monitoringCommandService.handle(new DeleteEmployeeCommand(employeeId));
        return ResponseEntity.noContent().build();
    }

    private EmployeeResource toResource(Employee employee) {
        return new EmployeeResource(
                employee.getId(),
                employee.getParkingId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getRole().getDisplayName(),
                employee.getSchedule().getDisplayName(),
                employee.getShiftStart(),
                employee.getShiftEnd(),
                employee.getAssignedSpot(),
                employee.getStatus().getDisplayName()
        );
    }
}
