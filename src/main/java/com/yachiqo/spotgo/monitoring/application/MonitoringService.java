package com.yachiqo.spotgo.monitoring.application;

import com.yachiqo.spotgo.monitoring.domain.model.aggregates.*;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.*;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.*;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.parking.application.ParkingTariffs;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static com.yachiqo.spotgo.monitoring.domain.model.valueobjects.MonitoringValues.*;

@Service @Transactional(readOnly=true)
public class MonitoringService {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
 private final OccupancySensorRepository sensors;private final OccupancyReadingRepository readings;
 private final SensorHealthEventRepository health;private final OccupancyConflictRepository conflicts;
 private final OccupancyAlertRepository alerts;private final CapacitySnapshotRepository snapshots;private final OccupancyReportRepository reports;
 private final TenantRepository tenants;private final ParkingZoneRepository zones;private final ParkingSpotRepository spots;
 private final ReservationRepository reservations;private final ParkingSessionRepository sessions;private final GuestParkingSessionRepository guests;
 private final EvidenceImageStorage evidenceStorage;
 private final MonitoringOccupancyService occupancy;private final MonitoringSubscriptionGateway subscriptions;private final MonitoringPolicy policy;private final Clock clock;
 public MonitoringService(OccupancySensorRepository sensors,OccupancyReadingRepository readings,SensorHealthEventRepository health,
  OccupancyConflictRepository conflicts,OccupancyAlertRepository alerts,CapacitySnapshotRepository snapshots,OccupancyReportRepository reports,
  TenantRepository tenants,ParkingZoneRepository zones,ParkingSpotRepository spots,ReservationRepository reservations,
  ParkingSessionRepository sessions,GuestParkingSessionRepository guests,MonitoringOccupancyService occupancy,MonitoringSubscriptionGateway subscriptions,MonitoringPolicy policy,Clock clock,EvidenceImageStorage evidenceStorage){
  this.sensors=sensors;this.readings=readings;this.health=health;this.conflicts=conflicts;this.alerts=alerts;this.snapshots=snapshots;this.reports=reports;
  this.tenants=tenants;this.zones=zones;this.spots=spots;this.reservations=reservations;this.sessions=sessions;this.guests=guests;this.occupancy=occupancy;this.subscriptions=subscriptions;this.policy=policy;this.clock=clock;this.evidenceStorage=evidenceStorage;
 }
 private static MonitoringException missing(String name){return new MonitoringException(HttpStatus.NOT_FOUND,name+" inexistente");}
 private static MonitoringException conflict(String message){return new MonitoringException(HttpStatus.CONFLICT,message);}
 private void readAccess(SpotgoUserPrincipal actor,UUID tenant){
  if(actor==null||actor.getRole()!=UserRole.SUPER_ADMIN&&(tenant==null||actor.getRole()!=UserRole.PARKING_ADMIN||!Objects.equals(tenant,actor.getTenantId())))throw new AccessDeniedException("Monitoring requiere administrar este Tenant");
 }
 private void writeAccess(SpotgoUserPrincipal actor,UUID tenant){
  if(actor==null||tenant==null||actor.getRole()!=UserRole.PARKING_ADMIN||!Objects.equals(tenant,actor.getTenantId()))throw new AccessDeniedException("Solo el administrador del Tenant modifica Monitoring");
 }
 private UUID scope(SpotgoUserPrincipal actor,UUID tenant){
  if(actor==null)throw new AccessDeniedException("Sesion requerida");
  if(actor.getRole()==UserRole.SUPER_ADMIN){if(tenant!=null)tenant(tenant);return tenant;}
  UUID id=tenant==null?actor.getTenantId():tenant;readAccess(actor,id);tenant(id);return id;
 }
 private Tenant tenant(UUID id){return tenants.findById(id).orElseThrow(()->missing("Tenant"));}
 private ParkingZone zone(UUID id){return zones.findById(id).orElseThrow(()->missing("Zona"));}
 private ParkingSpot spot(UUID id){return spots.findById(id).orElseThrow(()->missing("Espacio"));}
 public OccupancySensor sensor(SpotgoUserPrincipal actor,UUID id){var s=sensors.findById(id).orElseThrow(()->missing("Sensor"));readAccess(actor,s.getTenantId());return s;}
 private OccupancySensor lockSensor(SpotgoUserPrincipal actor,UUID id){
  var s=sensor(actor,id);writeAccess(actor,s.getTenantId());return lockSensor(s);
 }
 private OccupancySensor lockSensor(OccupancySensor s){
  tenants.lock(s.getTenantId()).orElseThrow(()->missing("Tenant"));spots.lock(s.getSpotId()).orElseThrow(()->missing("Espacio"));
  var locked=sensors.lock(s.getId()).orElseThrow(()->missing("Sensor"));entityManager.refresh(locked,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return locked;
 }
 public List<OccupancySensor> sensors(SpotgoUserPrincipal actor,UUID tenant){var id=scope(actor,tenant);return id==null?sensors.findAll():sensors.byTenant(id);}
 public List<OccupancyReading> readings(SpotgoUserPrincipal actor,UUID sensor){sensor(actor,sensor);return readings.bySensor(sensor);}
 public List<SensorHealthEvent> health(SpotgoUserPrincipal actor,UUID sensor){sensor(actor,sensor);return health.bySensor(sensor);}
 public SensorHealthEvent healthEvent(SpotgoUserPrincipal actor,UUID id){var e=health.findById(id).orElseThrow(()->missing("Evento de salud"));readAccess(actor,e.getSensor().getTenantId());return e;}
 public List<OccupancyConflict> conflicts(SpotgoUserPrincipal actor,UUID tenant){var id=scope(actor,tenant);return id==null?conflicts.findAll():conflicts.byTenant(id);}
 public OccupancyConflict conflict(SpotgoUserPrincipal actor,UUID id){var c=conflicts.findById(id).orElseThrow(()->missing("Conflicto"));readAccess(actor,c.getTenantId());return c;}
 public List<OccupancyAlert> alerts(SpotgoUserPrincipal actor,UUID tenant){var id=scope(actor,tenant);return id==null?alerts.findAll():alerts.byTenant(id);}
 public List<CapacitySnapshot> snapshots(SpotgoUserPrincipal actor,UUID tenant){var id=scope(actor,tenant);return id==null?snapshots.findAll():snapshots.byTenant(id);}
 public List<OccupancyReport> reports(SpotgoUserPrincipal actor,UUID tenant){var id=scope(actor,tenant);return id==null?reports.findAll():reports.byTenant(id);}
 public MonitoringOccupancyService.Observation observation(SpotgoUserPrincipal actor,UUID spot){readAccess(actor,spot(spot).getTenantId());return occupancy.observation(spot);}

 @Transactional
 public OccupancySensor register(SpotgoUserPrincipal actor,UUID spotId,SensorProtocol protocol,Map<String,Object> configuration,Instant installed,OccupancyStatus initialStatus,Instant occurred,BigDecimal quality){
  var current=spot(spotId);writeAccess(actor,current.getTenantId());tenants.lock(current.getTenantId()).orElseThrow(()->missing("Tenant"));
  var spot=spots.lock(spotId).orElseThrow(()->missing("Espacio"));var now=clock.instant();
  if(spot.getOperationalStatus()!=OperationalStatus.ACTIVE||spot.getZone().getStatus()!=ZoneStatus.ACTIVE||spot.getZone().getTenant().getStatus()!=TenantStatus.ACTIVE)throw conflict("Espacio, zona y Tenant deben estar habilitados");
  int limit=subscriptions.sensorLimit(spot.getTenantId());long count=sensors.byTenant(spot.getTenantId()).stream().filter(s->s.getStatus()==SensorStatus.ACTIVE).count();
  if(limit<1||count>=limit)throw conflict("La suscripcion empresarial no permite otro sensor activo");
  if(!sensors.activeSpot(spotId).isEmpty())throw conflict("El espacio ya tiene un sensor ACTIVE");
  validateReadingTime(occurred,now);if(installed==null||installed.isAfter(occurred)||!policy.fresh(occurred,now))throw new IllegalArgumentException("La primera lectura debe ser real, reciente y posterior a la instalacion");
  if(configuration==null)throw new IllegalArgumentException("configuration obligatorio");
  try{if(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(configuration).length()>8192)throw new IllegalArgumentException("configuration excede 8192 caracteres");}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("configuration invalido");}
  if(protocol==SensorProtocol.MQTT)text(Objects.toString(configuration.get("topic"),""));
  var sensor=sensors.saveAndFlush(new OccupancySensor(spot,protocol,configuration,installed,now));
  receive(sensor,initialStatus,occurred,quality,now);return sensor;
 }
 @Transactional
 public OccupancySensor deactivate(SpotgoUserPrincipal actor,UUID id){var s=lockSensor(actor,id);s.deactivate();return sensors.saveAndFlush(s);}
 private void validateReadingTime(Instant occurred,Instant received){if(occurred==null||occurred.isAfter(received))throw new IllegalArgumentException("occurred_at debe ser una fecha real no futura");}
 @Transactional
 public OccupancyReading receive(SpotgoUserPrincipal actor,UUID id,OccupancyStatus status,Instant occurred,BigDecimal quality){
  var s=lockSensor(actor,id);if(s.getStatus()!=SensorStatus.ACTIVE)throw conflict("Sensor inactivo");return receive(s,status,occurred,quality,clock.instant());
 }
 private OccupancyReading receive(OccupancySensor sensor,OccupancyStatus status,Instant occurred,BigDecimal quality,Instant received){
  validateReadingTime(occurred,received);if(occurred.isBefore(sensor.getInstalledAt()))throw new IllegalArgumentException("Lectura anterior a la instalacion");
  if(status==null||quality==null||quality.signum()<0||quality.compareTo(BigDecimal.valueOf(100))>0||quality.scale()>2)throw new IllegalArgumentException("Estado y calidad 0..100 con dos decimales requeridos");
  String hash;
  try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((sensor.getId()+"|"+status+"|"+occurred+"|"+quality.setScale(2)).getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  var replay=readings.byHash(sensor.getId(),hash);if(replay.isPresent())return replay.get();
  if(readings.bySensor(sensor.getId()).stream().anyMatch(r->r.getOccurredAt().equals(occurred)))throw conflict("Otra medicion distinta ya utiliza ese instante del sensor");
  var reading=readings.saveAndFlush(new OccupancyReading(sensor,status,occurred,received,quality,hash));sensor.seen(received);sensors.saveAndFlush(sensor);
  var latest=readings.bySensor(sensor.getId()).get(0);
  if(latest.getId().equals(reading.getId())&&policy.fresh(occurred,received)){
   if(policy.reliable(quality)){
    for(var event:health.bySensor(sensor.getId()))if(event.getRecoveredAt()==null&&(event.getReason().startsWith("COMMUNICATION_TIMEOUT")||event.getReason().startsWith("LOW_QUALITY")||event.getReason().startsWith("STALE_MEASUREMENT"))){event.recover(received);closeHealthAlerts(event);}
    evaluate(reading);
    captureIfReliable(sensor.getTenant(),sensor.getZone(),received);captureIfReliable(sensor.getTenant(),null,received);
   }else problem(sensor,SensorHealth.DEGRADED,"LOW_QUALITY: lectura inferior al umbral configurado",received);
  }
  return reading;
 }
 private void closeHealthAlerts(SensorHealthEvent event){for(var alert:alerts.byHealthEvent(event.getId()))alert.close(clock.instant());}
 private SensorHealthEvent problem(OccupancySensor sensor,SensorHealth state,String reason,Instant now){
  var replay=health.bySensor(sensor.getId()).stream().filter(e->e.getRecoveredAt()==null&&e.getHealth()==state&&e.getReason().equals(reason)).findFirst();
  if(replay.isPresent())return replay.get();
  var event=health.saveAndFlush(new SensorHealthEvent(sensor,state,reason,now));alerts.saveAndFlush(new OccupancyAlert(event,null,now));return event;
 }
 @Transactional
 public SensorHealthEvent recordHealth(SpotgoUserPrincipal actor,UUID sensorId,SensorHealth state,String reason){
  var s=lockSensor(actor,sensorId);if(s.getStatus()!=SensorStatus.ACTIVE)throw conflict("Sensor inactivo");var now=clock.instant();
  if(state==null)throw new IllegalArgumentException("health obligatorio");reason=text(reason);
  if(state==SensorHealth.HEALTHY){
   for(var event:health.bySensor(sensorId))if(event.getRecoveredAt()==null){event.recover(now);closeHealthAlerts(event);}
   return health.saveAndFlush(new SensorHealthEvent(s,state,reason,now));
  }
  return problem(s,state,reason,now);
 }
 @Transactional
 public SensorHealthEvent recoverHealth(SpotgoUserPrincipal actor,UUID id){
  var e=healthEvent(actor,id);lockSensor(actor,e.getSensorId());e.recover(clock.instant());closeHealthAlerts(e);return health.saveAndFlush(e);
 }

 private void evaluate(OccupancyReading reading){
  var observation=occupancy.observation(reading.getSpotId());
  if(!reading.getId().equals(observation.readingId()))return;
  var spot=reading.getSpot();var time=reading.getOccurredAt();
  var stays=sessions.byTenant(spot.getTenantId()).stream().filter(stay->stay.getSpotId().equals(spot.getId())&&!time.isBefore(stay.getEntryAt())&&(stay.getExitAt()==null||time.isBefore(stay.getExitAt()))).toList();
  boolean guest=guests.byTenant(spot.getTenantId()).stream().anyMatch(stay->stay.getSpotId().equals(spot.getId())&&!time.isBefore(stay.getEntryAt())&&(stay.getExitAt()==null||time.isBefore(stay.getExitAt())));
  var reserved=reservations.overlappingSpot(spot.getId(),time,time.plusNanos(1)).stream().filter(r->r.getStatus()==ReservationStatus.RESERVED||r.getStatus()==ReservationStatus.ACTIVE).findFirst().orElse(null);
  ConflictType type=null;Reservation reservation=null;
  if(reading.getOccupancyStatus()==OccupancyStatus.OCCUPIED){
   if(!stays.isEmpty()){
    var stay=stays.get(0);var r=stay.getReservation();
    if(time.isAfter(r.getEndAt().plusSeconds(ParkingTariffs.toleranceMinutes(r.getTenant().getRules())*60))){type=ConflictType.OVERSTAY;reservation=r;}
   }else if(!guest){type=reserved==null?ConflictType.UNEXPECTED_OCCUPANCY:ConflictType.SPACE_NOT_AVAILABLE;reservation=reserved;}
  }else if(!stays.isEmpty()){type=ConflictType.EXPECTED_OCCUPANCY_MISSING;reservation=stays.get(0).getReservation();}
  var open=conflicts.openSpot(spot.getId());boolean exists=false;
  for(var c:open){
   if(c.getConflictType()==type&&Objects.equals(c.getReservationId(),reservation==null?null:reservation.getId()))exists=true;
   else {c.resolve();for(var a:alerts.byConflict(c.getId()))a.close(clock.instant());}
  }
  if(type!=null&&!exists){var c=conflicts.saveAndFlush(new OccupancyConflict(reading,reservation,type,clock.instant()));alerts.saveAndFlush(new OccupancyAlert(null,c,clock.instant()));}
 }
 @Transactional
 public OccupancyConflict updateConflict(SpotgoUserPrincipal actor,UUID id,boolean resolve){
  var c=conflict(actor,id);writeAccess(actor,c.getTenantId());tenants.lock(c.getTenantId()).orElseThrow(()->missing("Tenant"));spots.lock(c.getSpotId()).orElseThrow(()->missing("Espacio"));
  c=conflicts.lock(id).orElseThrow(()->missing("Conflicto"));entityManager.refresh(c,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  if(resolve){c.resolve();for(var a:alerts.byConflict(id))a.close(clock.instant());}else c.acknowledge();
  return conflicts.saveAndFlush(c);
 }
 @Transactional
 public OccupancyAlert updateAlert(SpotgoUserPrincipal actor,UUID id,boolean close){
  var a=alerts.findById(id).orElseThrow(()->missing("Alerta"));writeAccess(actor,a.getTenantId());tenants.lock(a.getTenantId()).orElseThrow(()->missing("Tenant"));
  a=alerts.lock(id).orElseThrow(()->missing("Alerta"));entityManager.refresh(a,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  if(close){
   if(a.getConflict()!=null&&a.getConflict().getStatus()!=ConflictStatus.RESOLVED||a.getHealthEvent()!=null&&a.getHealthEvent().getRecoveredAt()==null)throw conflict("Resuelve primero el incidente que origino la alerta");
   a.close(clock.instant());
  }else {a.acknowledge(clock.instant());if(a.getConflict()!=null)a.getConflict().acknowledge();}
  return alerts.saveAndFlush(a);
 }
 @Transactional
 public OccupancyAlert uploadEvidence(SpotgoUserPrincipal actor,UUID id,byte[] image){
  var alert=alerts.findById(id).orElseThrow(()->missing("Alerta"));writeAccess(actor,alert.getTenantId());
  tenants.lock(alert.getTenantId()).orElseThrow(()->missing("Tenant"));
  alert=alerts.lock(id).orElseThrow(()->missing("Alerta"));entityManager.refresh(alert,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  String previous=alert.evidenceFileName();String stored=evidenceStorage.store(image);
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
   @Override public void afterCompletion(int status){
    if(status==STATUS_COMMITTED){if(previous!=null&&!previous.equals(stored))evidenceStorage.remove(previous);}
    else evidenceStorage.remove(stored);
   }
  });
  alert.attachEvidenceFile(stored);return alerts.saveAndFlush(alert);
 }
 @Transactional
 public EvidenceImageStorage.StoredImage evidence(SpotgoUserPrincipal actor,UUID id){
  var alert=alerts.findById(id).orElseThrow(()->missing("Alerta"));readAccess(actor,alert.getTenantId());
  alert=alerts.lock(id).orElseThrow(()->missing("Alerta"));entityManager.refresh(alert,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  String fileName=alert.evidenceFileName();
  if(fileName==null)throw missing("Imagen de evidencia");
  return evidenceStorage.read(fileName);
 }
 private List<ParkingSpot> operationalSpots(Tenant tenant,ParkingZone zone){
  return spots.findAll().stream().filter(s->s.getTenantId().equals(tenant.getId())&&(zone==null||s.getZoneId().equals(zone.getId())))
   .filter(s->s.getOperationalStatus()==OperationalStatus.ACTIVE&&s.getZone().getStatus()==ZoneStatus.ACTIVE&&tenant.getStatus()==TenantStatus.ACTIVE).toList();
 }
 private CapacitySnapshot captureIfReliable(Tenant tenant,ParkingZone zone,Instant now){
  var selected=operationalSpots(tenant,zone);int occupied=0;
  for(var spot:selected){String state=occupancy.physicalOccupancy(spot.getId());if(state.equals("UNAVAILABLE"))return null;if(state.equals("OCCUPIED"))occupied++;}
  return snapshots.saveAndFlush(new CapacitySnapshot(tenant,zone,occupied,selected.size(),now));
 }
 @Transactional
 public CapacitySnapshot snapshot(SpotgoUserPrincipal actor,UUID tenantId,UUID zoneId){
  writeAccess(actor,tenantId);var t=tenants.lock(tenantId).orElseThrow(()->missing("Tenant"));var z=zoneId==null?null:zone(zoneId);
  if(z!=null&&!z.getTenantId().equals(tenantId))throw new AccessDeniedException("Zona de otro Tenant");
  var result=captureIfReliable(t,z,clock.instant());if(result==null)throw conflict("No se publica porcentaje: hay espacios sin una lectura confiable reciente");return result;
 }
 @Transactional
 public OccupancyReport report(SpotgoUserPrincipal actor,UUID zoneId,Instant from,Instant to){
  var z=zone(zoneId);writeAccess(actor,z.getTenantId());tenants.lock(z.getTenantId()).orElseThrow(()->missing("Tenant"));
  if(from==null||to==null||!from.isBefore(to)||to.isAfter(clock.instant())||Duration.between(from,to).compareTo(Duration.ofDays(366))>0)throw new IllegalArgumentException("Intervalo [inicio,fin) invalido, futuro o mayor a 366 dias");
  var selected=snapshots.interval(zoneId,from,to);if(selected.isEmpty())throw conflict("No existen snapshots confiables de esta zona en el intervalo");
  var peak=selected.stream().map(CapacitySnapshot::getPercentage).max(BigDecimal::compareTo).orElseThrow();
  var average=selected.stream().map(CapacitySnapshot::getPercentage).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(selected.size()),2,RoundingMode.HALF_UP);
  long count=alerts.byTenant(z.getTenantId()).stream().filter(a->a.getSpot().getZoneId().equals(zoneId)&&!a.getCreatedAt().isBefore(from)&&a.getCreatedAt().isBefore(to)).count();
  var totals=Map.<String,Object>of("peak_occupancy_percentage",peak,"average_sample_occupancy_percentage",average,"snapshots_count",selected.size(),"alerts_count",count);
  return reports.saveAndFlush(new OccupancyReport(z,from,to,totals,clock.instant()));
 }
 @Transactional
 public void checkHealth(){
  var now=clock.instant();if(now==null)return;
  for(var current:sensors.findAll())if(current.getStatus()==SensorStatus.ACTIVE){
   var sensor=lockSensor(current);
   if(sensor.getStatus()!=SensorStatus.ACTIVE)continue;
   if(!policy.fresh(sensor.getLastSeenAt(),now))problem(sensor,SensorHealth.FAILED,"COMMUNICATION_TIMEOUT: sin mensajes dentro del umbral",now);
   else {
    var all=readings.bySensor(sensor.getId());
    if(all.isEmpty()||!policy.fresh(all.get(0).getOccurredAt(),now))problem(sensor,SensorHealth.DEGRADED,"STALE_MEASUREMENT: mensajes sin una medicion reciente",now);
    else if(!policy.reliable(all.get(0).getQuality()))problem(sensor,SensorHealth.DEGRADED,"LOW_QUALITY: lectura inferior al umbral configurado",now);
    else evaluate(all.get(0));
   }
  }
 }
}
