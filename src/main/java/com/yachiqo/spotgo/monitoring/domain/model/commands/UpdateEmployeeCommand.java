package com.yachiqo.spotgo.monitoring.domain.model.commands;

import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeRole;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeSchedule;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeStatus;

public record UpdateEmployeeCommand(
        String employeeId,
        String firstName,
        String lastName,
        EmployeeRole role,
        EmployeeSchedule schedule,
        String shiftStart,
        String shiftEnd,
        String assignedSpot,
        EmployeeStatus status
) {
}
