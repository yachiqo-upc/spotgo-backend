package com.yachiqo.spotgo.shared.interfaces.rest.transform;

import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.interfaces.rest.resource.ErrorResource;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@NullMarked
public class ErrorResponseAssembler {
    private static final String MESSAGES_BASENAME = "messages";

    private ErrorResponseAssembler() {
    }

    public static ResponseEntity<ErrorResource> toErrorResponseFromApplicationError(ApplicationError error) {
        HttpStatusCode status = toStatusFromErrorCode(error.code());
        String localizeMessage = toLocalizedMessageFromApplicationError(error);
        ErrorResource resource = new ErrorResource(error.code(), localizeMessage, error.details());
        return new ResponseEntity<>(resource, status);
    }

    private static String toLocalizedMessageFromApplicationError(ApplicationError error) {
        String specificKey = toSpecificMessageKeyFromErrorCode(error.code());
        String specificMessage = toLocalizedMessageOrNull(specificKey, error.details(), toEntityNameFromErrorCode(error.code()));
        if (specificMessage != null) {
            return specificMessage;
        }

        String fallbackKey = toMessageKeyFromErrorCode(error.code());
        return toLocalizeMessageWithFallback(fallbackKey, error.message(), error.details(), toEntityNameFromErrorCode(error.code()));
    }


    private static String toSpecificMessageKeyFromErrorCode(String errorCode) {
        return "error.%s.message".formatted(errorCode.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static String toMessageKeyFromErrorCode(String errorCode) {
        return switch (errorCode) {
            case "VALIDATION_ERROR" -> "error.validation.message";
            case "BUSINESS_RULE_VIOLATION" -> "error.business-rule.message";
            case "UNEXPECTED_ERROR" -> "error.unexpected.message";
            case String s when s.endsWith("_CONFLICT") -> "error.conflict.message";
            case String s when s.endsWith("_NOT_FOUND") -> "error.not-found.message";
            default -> "error.generic.message";
        };
    }

    private static HttpStatusCode toStatusFromErrorCode(String errorCode) {
        return switch (errorCode) {
            case "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            case "BUSINESS_RULE_VIOLATION" -> HttpStatusCode.valueOf(422);
            case "UNEXPECTED_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            case String s when s.endsWith("_CONFLICT") -> HttpStatus.CONFLICT;
            case String s when s.endsWith("_NOT_FOUND") -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String toLocalizeMessageWithFallback(String key, String fallback, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(MESSAGES_BASENAME, locale);
            if (!bundle.containsKey(key)) {
                return fallback;
            }
            String template = bundle.getString(key);
            return MessageFormat.format(template, args);
        } catch (MissingResourceException ex) {
            return fallback;
        }
    }

    private static String toLocalizedMessageOrNull(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(MESSAGES_BASENAME, locale);
            if (!bundle.containsKey(key)) {
                return null;
            }
            String template = bundle.getString(key);
            return MessageFormat.format(template, args);
        } catch (MissingResourceException ex) {
            return null;
        }
    }

    private static String toEntityNameFromErrorCode(String errorCode) {
        if (errorCode.endsWith("_CONFLICT")) {
            return errorCode.replace("_CONFLICT", "").toLowerCase(Locale.ROOT);
        }
        if (errorCode.endsWith("_NOT_FOUND")) {
            return errorCode.replace("_NOT_FOUND", "").toLowerCase(Locale.ROOT);
        }
        return "resource";
    }

}
