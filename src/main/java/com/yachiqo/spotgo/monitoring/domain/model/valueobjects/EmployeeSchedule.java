package com.yachiqo.spotgo.monitoring.domain.model.valueobjects;

public enum EmployeeSchedule {
    ALL_WEEK("all-week"),
    WEEKDAYS("weekdays"),
    WEEKENDS("weekends");

    private final String displayName;

    EmployeeSchedule(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static EmployeeSchedule fromDisplayName(String value) {
        for (var schedule : values()) {
            if (schedule.displayName.equalsIgnoreCase(value)) {
                return schedule;
            }
        }
        throw new IllegalArgumentException("Unknown employee schedule: " + value);
    }
}
