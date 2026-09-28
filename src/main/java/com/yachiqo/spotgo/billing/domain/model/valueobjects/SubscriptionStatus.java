package com.yachiqo.spotgo.billing.domain.model.valueobjects;

public enum SubscriptionStatus {
    ACTIVE,
    INACTIVE,
    CANCELLED;

    public static SubscriptionStatus fromDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Subscription status cannot be null");
        }
        try {
            return SubscriptionStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid subscription status: " + value, e);
        }
    }
}
