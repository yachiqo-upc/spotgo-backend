package com.yachiqo.spotgo.monitoring.domain.model.aggregates;

import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeRole;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeSchedule;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.EmployeeStatus;
import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.UuidIdentifiedAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "employees", indexes = {
    @Index(name = "idx_employee_parkingId", columnList = "parkingId")
})
@Getter
public class Employee extends UuidIdentifiedAggregateRoot<Employee> {

    @Column(name = "parkingId")
    private String parkingId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeSchedule schedule;

    @Column(nullable = false)
    private String shiftStart;

    @Column(nullable = false)
    private String shiftEnd;

    @Column(name = "assignedSpot")
    private String assignedSpot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeStatus status;

    public Employee() {
    }

    public Employee(String parkingId, String firstName, String lastName, EmployeeRole role,
                    EmployeeSchedule schedule, String shiftStart, String shiftEnd, String assignedSpot, EmployeeStatus status) {
        this.parkingId = parkingId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.schedule = schedule;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.assignedSpot = assignedSpot;
        this.status = status;
    }

    public void update(String firstName, String lastName, EmployeeRole role,
                       EmployeeSchedule schedule, String shiftStart, String shiftEnd, String assignedSpot, EmployeeStatus status) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.schedule = schedule;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.assignedSpot = assignedSpot;
        this.status = status;
    }
}
