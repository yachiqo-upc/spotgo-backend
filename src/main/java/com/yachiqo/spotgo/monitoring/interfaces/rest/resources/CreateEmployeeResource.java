package com.yachiqo.spotgo.monitoring.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateEmployeeResource(
        @Schema(description = "Parking identifier", example = "1")
        @NotBlank
        String parkingId,

        @Schema(description = "Employee first name", example = "Piero")
        @NotBlank
        String firstName,

        @Schema(description = "Employee last name", example = "Quiroz Montoya")
        @NotBlank
        String lastName,

        @Schema(description = "Employee role", example = "guard",
                allowableValues = {"guard", "cleaning-personnel"})
        @NotBlank
        String role,

        @Schema(description = "Work schedule", example = "all-week",
                allowableValues = {"all-week", "weekdays", "weekends"})
        @NotBlank
        String schedule,

        @Schema(description = "Shift start time", example = "09:00")
        @NotBlank
        String shiftStart,

        @Schema(description = "Shift end time", example = "17:00")
        @NotBlank
        String shiftEnd,

        @Schema(description = "Assigned parking spot code", example = "A1")
        String assignedSpot,

        @Schema(description = "Employee status", example = "on-duty",
                allowableValues = {"on-duty", "off-duty"})
        @NotBlank
        String status
) {}
