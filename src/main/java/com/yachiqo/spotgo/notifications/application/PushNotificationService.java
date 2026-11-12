package com.yachiqo.spotgo.notifications.application;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.AssignmentStatus;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.RoleAssignmentRepository;
import com.yachiqo.spotgo.notifications.domain.model.PushDeviceToken;
import com.yachiqo.spotgo.notifications.infrastructure.persistence.PushDeviceTokenRepository;
import com.yachiqo.spotgo.shared.domain.events.SpotgoDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;

@Service
public class PushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final int MAX_BATCH_SIZE = 500;

    private final PushDeviceTokenRepository devices;
    private final RoleAssignmentRepository roles;
    private final PushNotificationGateway gateway;
    private final Clock clock;

    public PushNotificationService(PushDeviceTokenRepository devices, RoleAssignmentRepository roles,
                                   PushNotificationGateway gateway, Clock clock) {
        this.devices = devices;
        this.roles = roles;
        this.gateway = gateway;
        this.clock = clock;
    }

    public void send(SpotgoDomainEvent event) {
        PushNotificationContent content = PushNotificationContent.from(event);
        if (content == null) return;

        var recipients = new LinkedHashSet<>(event.recipientAccountIds());
        if (event.tenantId() != null) {
            recipients.addAll(roles.findEffectiveAccountIdsByTenantAndRole(event.tenantId(), UserRole.PARKING_ADMIN,
                    AssignmentStatus.ACTIVE, clock.instant()));
        }
        if (recipients.isEmpty()) return;

        var registrations = devices.findAllForAccounts(recipients);
        if (registrations.isEmpty()) return;
        Map<String, String> data = eventData(event);
        for (int start = 0; start < registrations.size(); start += MAX_BATCH_SIZE) {
            var batch = registrations.subList(start, Math.min(start + MAX_BATCH_SIZE, registrations.size()));
            var invalid = gateway.send(batch.stream().map(PushDeviceToken::getInstallationId).toList(),
                    content.title(), content.body(), data);
            if (!invalid.isEmpty()) {
                devices.deleteAll(batch.stream().filter(device -> invalid.contains(device.getInstallationId())).toList());
            }
        }
    }

    private static Map<String, String> eventData(SpotgoDomainEvent event) {
        var data = new LinkedHashMap<String, String>(event.data());
        data.put("event_id", event.eventId().toString());
        data.put("event_type", event.type());
        data.put("source_context", event.sourceContext());
        data.put("aggregate_type", event.aggregateType());
        data.put("aggregate_id", event.aggregateId().toString());
        if (event.tenantId() != null) data.put("tenant_id", event.tenantId().toString());
        return Collections.unmodifiableMap(data);
    }
}
