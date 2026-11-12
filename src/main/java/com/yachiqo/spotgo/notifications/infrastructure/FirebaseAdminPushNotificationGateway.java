package com.yachiqo.spotgo.notifications.infrastructure;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.yachiqo.spotgo.notifications.application.PushNotificationGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FirebaseAdminPushNotificationGateway implements PushNotificationGateway {
    private final FirebaseMessaging messaging;

    public FirebaseAdminPushNotificationGateway(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public List<String> send(List<String> installationIds, String title, String body, Map<String, String> data) {
        if (installationIds.isEmpty()) return List.of();
        var messages = installationIds.stream().map(installationId -> Message.builder()
                .setFid(installationId)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build()).toList();
        try {
            var response = messaging.sendEach(messages);
            var invalidInstallations = new ArrayList<String>();
            for (int i = 0; i < response.getResponses().size(); i++) {
                var result = response.getResponses().get(i);
                if (result.isSuccessful()) continue;
                FirebaseMessagingException error = result.getException();
                if (error != null && error.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED)
                    invalidInstallations.add(installationIds.get(i));
            }
            return List.copyOf(invalidInstallations);
        } catch (FirebaseMessagingException exception) {
            throw new IllegalStateException("Firebase Cloud Messaging rejected the installation batch.", exception);
        }
    }
}
