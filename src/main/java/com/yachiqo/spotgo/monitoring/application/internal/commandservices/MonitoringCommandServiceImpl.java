package com.yachiqo.spotgo.monitoring.application.internal.commandservices;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.monitoring.domain.model.commands.CreateEmployeeCommand;
import com.yachiqo.spotgo.monitoring.domain.model.commands.DeleteEmployeeCommand;
import com.yachiqo.spotgo.monitoring.domain.model.commands.UpdateEmployeeCommand;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class MonitoringCommandServiceImpl implements MonitoringCommandService {

    private final EmployeeRepository employeeRepository;

    public MonitoringCommandServiceImpl(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public Optional<Employee> handle(CreateEmployeeCommand command) {
        var employee = new Employee(
                command.parkingId(), command.firstName(), command.lastName(), command.role(),
                command.schedule(), command.shiftStart(), command.shiftEnd(), command.assignedSpot(), command.status());
        return Optional.of(employeeRepository.save(employee));
    }

    @Override
    public Optional<Employee> handle(UpdateEmployeeCommand command) {
        var employeeOpt = employeeRepository.findById(command.employeeId());
        if (employeeOpt.isEmpty()) return Optional.empty();
        var employee = employeeOpt.get();
        employee.update(
                command.firstName(), command.lastName(), command.role(),
                command.schedule(), command.shiftStart(), command.shiftEnd(), command.assignedSpot(), command.status());
        return Optional.of(employeeRepository.save(employee));
    }

    @Override
    public void handle(DeleteEmployeeCommand command) {
        employeeRepository.deleteById(command.employeeId());
    }
}
