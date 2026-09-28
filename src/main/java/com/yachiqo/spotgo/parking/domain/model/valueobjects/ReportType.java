package com.yachiqo.spotgo.parking.domain.model.valueobjects;

public enum ReportType {
    SAFETY_CONCERN("safety-concern"),
    MAINTENANCE_ISSUE("maintenance-issue"),
    BILLING_DISPUTE("billing-dispute"),
    OTHER("other");

    private final String displayName;

    ReportType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ReportType fromDisplayName(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("Display name cannot be null");
        }
        for (ReportType t : values()) {
            if (t.displayName.equals(displayName)) return t;
        }
        throw new IllegalArgumentException("Invalid ReportType: " + displayName);
    }
}
