package com.yachiqo.spotgo.monitoring.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmployeeResource(
        @Schema(description = "Unique identifier of the employee", example = "1")
        String id,

        @Schema(description = "Parking identifier", example = "1")
        String parkingId,

        @Schema(description = "Employee first name", example = "Piero")
        String firstName,

        @Schema(description = "Employee last name", example = "Quiroz Montoya")
        String lastName,

        @Schema(description = "Employee role", example = "guard",
                allowableValues = {"guard", "cleaning-personnel"})
        String role,

        @Schema(description = "Work schedule", example = "all-week",
                allowableValues = {"all-week", "weekdays", "weekends"})
        String schedule,

        @Schema(description = "Shift start time", example = "09:00")
        String shiftStart,

        @Schema(description = "Shift end time", example = "17:00")
        String shiftEnd,

        @Schema(description = "Assigned parking spot code", example = "A1")
        String assignedSpot,

        @Schema(description = "Employee status", example = "on-duty",
                allowableValues = {"on-duty", "off-duty"})
        String status
) {}
