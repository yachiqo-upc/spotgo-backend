package com.yachiqo.spotgo.iam.infrastructure.notifications;

import com.yachiqo.spotgo.iam.application.PasswordResetNotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class ResendPasswordResetNotificationService implements PasswordResetNotificationService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String fromEmail;
    private final String resendUrl;

    @Autowired
    public ResendPasswordResetNotificationService(
            @Value("${app.password-reset.resend-api-key:${RESEND_API_KEY:}}") String apiKey,
            @Value("${app.password-reset.from-email:${APP_PASSWORD_RESET_FROM_EMAIL:}}") String fromEmail,
            @Value("${app.password-reset.resend-url:https://api.resend.com/emails}") String resendUrl) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), apiKey, fromEmail, resendUrl);
    }

    ResendPasswordResetNotificationService(HttpClient httpClient,
                                           ObjectMapper objectMapper,
                                           String apiKey,
                                           String fromEmail,
                                           String resendUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.resendUrl = resendUrl;
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        sendCode(email, "SpotGo password reset code",
                "Your SpotGo password reset code is: " + code + "\n\nThis code can be used only once and expires soon.");
    }

    @Override
    public void sendAccountInvitationCode(String email, String code) {
        sendCode(email, "You are invited to SpotGo",
                "Your SpotGo account is ready. Use this one-time code to set your password in the app: "
                        + code + "\n\nThe code expires soon and can be used only once.");
    }

    private void sendCode(String email, String subject, String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing Resend API key configuration");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Missing password reset sender email configuration");
        }

        var payload = new ResendEmailRequest(
                fromEmail,
                List.of(email),
                subject,
                text);

        var request = HttpRequest.newBuilder(URI.create(resendUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Resend email request failed with status "
                                + response.statusCode()
                                + ": "
                                + truncate(response.body()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send password reset email via Resend", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Password reset email request was interrupted", e);
        }
    }

    private String toJson(ResendEmailRequest payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Resend email request", e);
        }
    }

    private String truncate(String body) {
        if (body == null || body.length() <= MAX_ERROR_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String text) {
    }
}
