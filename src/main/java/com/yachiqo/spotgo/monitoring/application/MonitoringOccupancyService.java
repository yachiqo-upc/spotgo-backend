package com.yachiqo.spotgo.monitoring.application;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.*;
import com.yachiqo.spotgo.parking.application.ParkingOccupancyGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service @Transactional(readOnly=true)
public class MonitoringOccupancyService implements ParkingOccupancyGateway {
 private final OccupancySensorRepository sensors;private final OccupancyReadingRepository readings;
 private final SensorHealthEventRepository health;private final MonitoringSubscriptionGateway subscriptions;
 private final MonitoringPolicy policy;private final Clock clock;
 public MonitoringOccupancyService(OccupancySensorRepository sensors,OccupancyReadingRepository readings,SensorHealthEventRepository health,MonitoringSubscriptionGateway subscriptions,MonitoringPolicy policy,Clock clock){
  this.sensors=sensors;this.readings=readings;this.health=health;this.subscriptions=subscriptions;this.policy=policy;this.clock=clock;
 }
 public record Observation(UUID spotId,String occupancy,UUID readingId,Instant occurredAt,String reason){}
 public Observation observation(UUID spot){
  var active=sensors.activeSpot(spot);if(active.size()!=1)return unavailable(spot,"NO_ACTIVE_SENSOR");
  var sensor=active.get(0);
  if(subscriptions.sensorLimit(sensor.getTenantId())<1)return unavailable(spot,"TENANT_SUBSCRIPTION_NOT_ACTIVE");
  if(health.bySensor(sensor.getId()).stream().anyMatch(e->e.getRecoveredAt()==null&&e.getHealth()!=SensorHealth.HEALTHY))return unavailable(spot,"SENSOR_HEALTH");
  var all=readings.bySensor(sensor.getId());if(all.isEmpty())return unavailable(spot,"NO_READING");
  var last=all.get(0);var now=clock.instant();
  if(!policy.fresh(last.getOccurredAt(),now)||!policy.fresh(sensor.getLastSeenAt(),now))return unavailable(spot,"STALE_READING");
  if(!policy.reliable(last.getQuality()))return unavailable(spot,"LOW_QUALITY");
  return new Observation(spot,last.getOccupancyStatus().name(),last.getId(),last.getOccurredAt(),"RELIABLE_READING");
 }
 private Observation unavailable(UUID id,String reason){return new Observation(id,"UNAVAILABLE",null,null,reason);}
 @Override public String physicalOccupancy(UUID spot){return observation(spot).occupancy();}
}
