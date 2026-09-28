package com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, String> {
    List<Employee> findByParkingId(String parkingId);
    Optional<Employee> findByParkingIdAndAssignedSpot(String parkingId, String assignedSpot);
}
