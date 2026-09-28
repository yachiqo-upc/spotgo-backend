package com.yachiqo.spotgo.monitoring.domain.model.valueobjects;

public enum EmployeeStatus {
    ON_DUTY("on-duty"),
    OFF_DUTY("off-duty");

    private final String displayName;

    EmployeeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static EmployeeStatus fromDisplayName(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("Display name cannot be null");
        }
        for (EmployeeStatus s : values()) {
            if (s.displayName.equals(displayName)) return s;
        }
        throw new IllegalArgumentException("Invalid EmployeeStatus: " + displayName);
    }
}
