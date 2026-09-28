package com.yachiqo.spotgo.iam.domain.model.valueobjects;

public enum UserRole {
    ADMIN,
    CLIENT;

    public static UserRole fromDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("User role cannot be null");
        }
        try {
            return UserRole.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid user role: " + value, e);
        }
    }
}
