package com.yachiqo.spotgo.parking.domain.model.valueobjects;

public enum SpotStatus {
    AVAILABLE("available"),
    OCCUPIED("occupied"),
    RESERVED("reserved"),
    MAINTENANCE("maintenance");

    private final String displayName;

    SpotStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SpotStatus fromDisplayName(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("Display name cannot be null");
        }
        for (SpotStatus s : values()) {
            if (s.displayName.equals(displayName)) return s;
        }
        throw new IllegalArgumentException("Invalid SpotStatus: " + displayName);
    }
}
