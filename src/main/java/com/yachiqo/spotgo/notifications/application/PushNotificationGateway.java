package com.yachiqo.spotgo.notifications.application;

import java.util.List;
import java.util.Map;

public interface PushNotificationGateway {
    /** Sends to at most 500 Firebase installations and returns IDs no longer registered. */
    List<String> send(List<String> installationIds, String title, String body, Map<String, String> data);
}
