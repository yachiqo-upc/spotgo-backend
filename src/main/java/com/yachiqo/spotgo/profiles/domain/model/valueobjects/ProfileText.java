package com.yachiqo.spotgo.profiles.domain.model.valueobjects;
public final class ProfileText {
    private ProfileText() {}
    public static String required(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
}
