package com.yachiqo.spotgo.monitoring.application;
import java.util.UUID;
public interface MonitoringSubscriptionGateway {
 int sensorLimit(UUID tenant);
 boolean spotLicensed(UUID tenant,UUID spot);
}
