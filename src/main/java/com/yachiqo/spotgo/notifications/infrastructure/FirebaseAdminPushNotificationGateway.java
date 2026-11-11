package com.yachiqo.spotgo.notifications.infrastructure;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
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
    public List<String> send(List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens.isEmpty()) return List.of();
        var message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build();
        try {
            var response = messaging.sendEachForMulticast(message);
            var invalidTokens = new ArrayList<String>();
            for (int i = 0; i < response.getResponses().size(); i++) {
                var result = response.getResponses().get(i);
                if (result.isSuccessful()) continue;
                FirebaseMessagingException error = result.getException();
                if (error != null && error.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED)
                    invalidTokens.add(tokens.get(i));
            }
            return List.copyOf(invalidTokens);
        } catch (FirebaseMessagingException exception) {
            throw new IllegalStateException("Firebase Cloud Messaging rejected the notification batch.", exception);
        }
    }
}
