package com.yachiqo.spotgo.monitoring.application.internal.commandservices;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.monitoring.domain.model.commands.CreateEmployeeCommand;
import com.yachiqo.spotgo.monitoring.domain.model.commands.DeleteEmployeeCommand;
import com.yachiqo.spotgo.monitoring.domain.model.commands.UpdateEmployeeCommand;

import java.util.Optional;

public interface MonitoringCommandService {
    Optional<Employee> handle(CreateEmployeeCommand command);
    Optional<Employee> handle(UpdateEmployeeCommand command);
    void handle(DeleteEmployeeCommand command);
}
