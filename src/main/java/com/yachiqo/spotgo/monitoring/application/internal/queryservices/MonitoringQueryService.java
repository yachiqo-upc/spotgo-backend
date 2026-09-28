package com.yachiqo.spotgo.monitoring.application.internal.queryservices;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.OccupancyByHour;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.WeeklyTrend;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetAllEmployeesQuery;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetAllOccupancyByHourQuery;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetAllWeeklyTrendsQuery;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetOccupancyByHourByParkingIdQuery;
import com.yachiqo.spotgo.monitoring.domain.model.queries.GetWeeklyTrendsByParkingIdQuery;

import java.util.List;

public interface MonitoringQueryService {
    List<Employee> handle(GetAllEmployeesQuery query);
    List<OccupancyByHour> handle(GetAllOccupancyByHourQuery query);
    List<OccupancyByHour> handle(GetOccupancyByHourByParkingIdQuery query);
    List<WeeklyTrend> handle(GetAllWeeklyTrendsQuery query);
    List<WeeklyTrend> handle(GetWeeklyTrendsByParkingIdQuery query);
}
