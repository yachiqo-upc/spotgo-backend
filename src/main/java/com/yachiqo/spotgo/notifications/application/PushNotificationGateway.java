package com.yachiqo.spotgo.notifications.application;

import java.util.List;
import java.util.Map;

public interface PushNotificationGateway {
    /** Sends to at most 500 registrations and returns tokens that Firebase says are no longer registered. */
    List<String> send(List<String> tokens, String title, String body, Map<String, String> data);
}
