package com.yachiqo.spotgo.parking.domain.model.valueobjects;

public enum ReportStatus {
    SUBMITTED("submitted"),
    RESOLVED("resolved");

    private final String displayName;

    ReportStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ReportStatus fromDisplayName(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("Display name cannot be null");
        }
        for (ReportStatus s : values()) {
            if (s.displayName.equals(displayName)) return s;
        }
        throw new IllegalArgumentException("Invalid ReportStatus: " + displayName);
    }
}
