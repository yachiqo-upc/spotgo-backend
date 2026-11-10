package com.yachiqo.spotgo.monitoring.application;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class MonitoringMaintenance {
 private final MonitoringService service;
 public MonitoringMaintenance(MonitoringService service){this.service=service;}
 @Scheduled(fixedDelayString="${app.monitoring.health-check-ms:60000}",initialDelayString="${app.monitoring.health-check-ms:60000}")
 public void check(){service.checkHealth();}
}