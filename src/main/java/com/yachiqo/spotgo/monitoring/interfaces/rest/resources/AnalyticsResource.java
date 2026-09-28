package com.yachiqo.spotgo.monitoring.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AnalyticsResource(
        @Schema(description = "Parking identifier", example = "1")
        String id,
        @Schema(description = "Administrator identifier", example = "1")
        String adminId,
        @Schema(description = "Parking name", example = "Parking Central Lima")
        String name,
        @Schema(description = "Total number of spaces", example = "28")
        Integer totalSpaces,
        @Schema(description = "Currently available spaces", example = "12")
        Integer availableSpaces,
        @Schema(description = "Average occupancy percentage for the selected period", example = "84.2")
        Double averageOccupancy,
        @Schema(description = "Occupancy trend versus previous equal period, in percent", example = "5.3")
        Double occupancyTrendPercent,
        @Schema(description = "Hour with the highest occupancy", example = "14:00")
        String peakHour,
        @Schema(description = "Estimated revenue for the selected period", example = "4200.0")
        Double totalRevenue,
        @Schema(description = "Revenue trend versus previous equal period, in percent", example = "8.7")
        Double revenueTrendPercent,
        @Schema(description = "Current system status", example = "active")
        String systemStatus,
        @Schema(description = "Total capacity", example = "120")
        Integer totalCapacity,
        @Schema(description = "Operational efficiency index", example = "8.4")
        Double efficiencyIndex,
        @Schema(description = "Count of spots currently in maintenance", example = "2")
        Integer maintenanceSpotsCount,
        List<OccupancyByHourResource> occupancyByHour,
        List<WeeklyTrendResource> weeklyTrends,
        List<SpotUtilizationResource> mostUtilizedSpots
) {}
