package com.yachiqo.spotgo.shared.application.result;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ApplicationError(
        String code,
        String message,
        String details
) {

    public ApplicationError(String code, String message) {
        this(code, message, null);
    }

    public static ApplicationError validationError(String fieldOrConcept, String reason)
    {
        return new ApplicationError("VALIDATION_ERROR",
                "Validation failed for %s".formatted(fieldOrConcept),
                reason);
    }

    public static ApplicationError businessRuleViolation(String rule, String reason) {
        return new ApplicationError("BUSINESS_RULE_VIOLATION",
                "Business rule violation: %s".formatted(rule),
                reason);
    }

    public static ApplicationError conflict(String resource, String reason) {
        return new ApplicationError("%s_CONFLICT".formatted(resource.toUpperCase()),
                "Conflict with %s".formatted(resource),
                reason);
    }


    public static ApplicationError unexpected(String context, String reason) {
        return new ApplicationError("UNEXPECTED_ERROR",
                "Unexpected error in %s".formatted(context),
                reason);
    }

    public static ApplicationError notFound(String resource, String reason) {
        return new ApplicationError("%s_NOT_FOUND".formatted(resource.toUpperCase()),
                "%s not found".formatted(resource),
                reason);
    }
}
