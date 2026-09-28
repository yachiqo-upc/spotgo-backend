package com.yachiqo.spotgo.monitoring.application.internal.queryservices;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyByHour;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.WeeklyTrend;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetAllEmployeesQuery;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetAllOccupancyByHourQuery;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetAllWeeklyTrendsQuery;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetOccupancyByHourByParkingIdQuery;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetWeeklyTrendsByParkingIdQuery;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.OccupancyByHourRepository;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.WeeklyTrendRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MonitoringQueryServiceImpl implements MonitoringQueryService {

    private final EmployeeRepository employeeRepository;
    private final OccupancyByHourRepository occupancyByHourRepository;
    private final WeeklyTrendRepository weeklyTrendRepository;

    public MonitoringQueryServiceImpl(EmployeeRepository employeeRepository,
                                      OccupancyByHourRepository occupancyByHourRepository,
                                      WeeklyTrendRepository weeklyTrendRepository) {
        this.employeeRepository = employeeRepository;
        this.occupancyByHourRepository = occupancyByHourRepository;
        this.weeklyTrendRepository = weeklyTrendRepository;
    }

    @Override
    public List<Employee> handle(GetAllEmployeesQuery query) {
        return employeeRepository.findAll();
    }

    public List<Employee> getEmployeesByParkingId(String parkingId) {
        return employeeRepository.findByParkingId(parkingId);
    }

    @Override
    public List<OccupancyByHour> handle(GetAllOccupancyByHourQuery query) {
        return occupancyByHourRepository.findAll();
    }

    @Override
    public List<OccupancyByHour> handle(GetOccupancyByHourByParkingIdQuery query) {
        return occupancyByHourRepository.findByParkingId(query.parkingId());
    }

    @Override
    public List<WeeklyTrend> handle(GetAllWeeklyTrendsQuery query) {
        return weeklyTrendRepository.findAll();
    }

    @Override
    public List<WeeklyTrend> handle(GetWeeklyTrendsByParkingIdQuery query) {
        return weeklyTrendRepository.findByParkingId(query.parkingId());
    }
}
