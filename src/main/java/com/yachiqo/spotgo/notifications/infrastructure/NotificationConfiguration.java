package com.yachiqo.spotgo.notifications.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.yachiqo.spotgo.notifications.application.PushNotificationGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(FcmProperties.class)
public class NotificationConfiguration {
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("spotgo-notification-");
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.fcm.enabled", havingValue = "true")
    public FirebaseApp firebaseApp(FcmProperties properties) throws Exception {
        if (properties.getProjectId() == null || properties.getProjectId().isBlank())
            throw new IllegalStateException("FCM_PROJECT_ID is required when FCM_ENABLED=true.");
        if (properties.getServiceAccountJson() == null || properties.getServiceAccountJson().isBlank())
            throw new IllegalStateException("FCM_SERVICE_ACCOUNT_JSON is required when FCM_ENABLED=true.");

        var input = new ByteArrayInputStream(properties.getServiceAccountJson().getBytes(StandardCharsets.UTF_8));
        var credentials = GoogleCredentials.fromStream(input);
        var options = FirebaseOptions.builder().setCredentials(credentials).setProjectId(properties.getProjectId()).build();
        return FirebaseApp.initializeApp(options, "spotgo");
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.fcm.enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.fcm.enabled", havingValue = "true")
    public PushNotificationGateway firebasePushNotificationGateway(FirebaseMessaging messaging) {
        return new FirebaseAdminPushNotificationGateway(messaging);
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.fcm.enabled", havingValue = "false", matchIfMissing = true)
    public PushNotificationGateway disabledPushNotificationGateway() {
        return (tokens, title, body, data) -> java.util.List.of();
    }
}
