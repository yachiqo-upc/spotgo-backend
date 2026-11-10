package com.yachiqo.spotgo.monitoring.application;
import com.yachiqo.spotgo.billing.application.*;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.*;
import com.yachiqo.spotgo.monitoring.domain.model.valueobjects.*;
import com.yachiqo.spotgo.monitoring.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryServiceImpl;
import com.yachiqo.spotgo.billing.domain.model.aggregates.*;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.*;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.application.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.RoleAssignmentRepository;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandServiceImpl;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.*;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.BigDecimal;
import java.time.*;
import java.net.*;
import java.net.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:monitoring-integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE,HOUR,ROWNUM,DAY",
 "app.parking.lock-cleanup-ms=3600000", "app.monitoring.health-check-ms=3600000"
})
@ActiveProfiles("test")
class MonitoringIntegrationTest {
 @org.junit.jupiter.api.io.TempDir static java.nio.file.Path evidenceDirectory;
 @org.springframework.test.context.DynamicPropertySource
 static void evidenceProperties(org.springframework.test.context.DynamicPropertyRegistry registry){registry.add("app.monitoring.evidence-directory",()->evidenceDirectory.toString());}
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 @Autowired MonitoringService monitoring;@Autowired MonitoringOccupancyService occupancy;
 @Autowired OccupancySensorRepository sensorRepository;@Autowired OccupancyReadingRepository readingRepository;
 @Autowired SensorHealthEventRepository healthRepository;@Autowired OccupancyConflictRepository conflictRepository;
 @Autowired OccupancyAlertRepository alertRepository;@Autowired CapacitySnapshotRepository snapshotRepository;@Autowired OccupancyReportRepository reportRepository;
 @Autowired ParkingQueryServiceImpl parkingQueries;
 @Autowired BillingService billing;@Autowired BillingQueryService queries;@Autowired ParkingCommandServiceImpl parking;
 @Autowired AuthenticationService iam;@Autowired JwtTokenService jwt;@Autowired SessionAuthenticationService authentication;
 @Autowired RoleAssignmentRepository roles;@Autowired ProfilesManagementService profiles;@Autowired VehicleRepository vehicles;
 @Autowired ReservationRepository reservations;@Autowired DigitalPaymentRepository payments;@Autowired PaymentAttemptRepository attempts;
 @Autowired OutstandingBalanceRepository balances;@Autowired AdditionalChargeRepository charges;@Autowired SubscriptionRepository subscriptions;
 @Autowired RefundRepository refunds;@Autowired BillingDocumentRepository documents;@Autowired JdbcTemplate jdbc;
 @MockitoBean PaymentProvider provider;@MockitoBean SubscriptionPlans plans;@MockitoBean Clock clock;
 @MockitoBean PasswordResetNotificationService notifications;@LocalServerPort int port;
 AtomicReference<Instant> now=new AtomicReference<>();
 AtomicReference<PaymentStatus> outcome=new AtomicReference<>();
 AuthenticatedUserResource driverAuth,otherAuth,adminAuth,superAuth;
 SpotgoUserPrincipal driver,other,admin,superAdmin;Tenant tenant;ParkingSpot spot,second;Vehicle vehicle,otherVehicle;
 Profile driverProfile,adminProfile;
 Map<String,Object> driverPlan=Map.of("code","DRIVER_TEST","audience","DRIVER","price",10,"billing_period_months",1,"discount_percent",20);
 Map<String,Object> tenantPlan=Map.of("code","TENANT_TEST","audience","PARKING_ADMIN","price",50,"billing_period_months",1,"included_sensors",20);
 Map<String,Object> rules(){return Map.of("tariff_rules",Map.of("period_minutes",60,"price_per_period",5,"rounding","CEILING"),"lock_duration_minutes",10,"overstay_tolerance_minutes",5,"allocation_rules",Map.of(),"coexistence_rules",List.of());}
 AuthenticatedUserResource register(String name){return iam.signUp(name+"@example.com","Password123!","TEST");}
 SpotgoUserPrincipal actor(AuthenticatedUserResource auth){return authentication.authenticate(jwt.parseAccessToken(auth.accessToken()));}
 @BeforeEach void setup(){
  now.set(Instant.parse("2030-01-01T10:00:00Z"));outcome.set(PaymentStatus.APPROVED);
  when(clock.instant()).thenAnswer(i->now.get());when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  for(String table:List.of("occupancy_alerts","occupancy_reports","capacity_snapshots","occupancy_conflicts","sensor_health_events","occupancy_readings","occupancy_sensors","billing_documents","refunds","outstanding_balances","additional_charges","subscriptions","payment_attempts",
   "parking_sessions","guest_parking_sessions","reservations","digital_parking_maps","floor_plans","parking_spots","parking_zones",
   "driver_classifications","digital_payments","payment_tokens","vehicles","profiles","audit_entries","sessions","credentials","role_assignments","passwordResetCodes","accounts","tenants"))jdbc.update("DELETE FROM "+table);
  when(plans.get("DRIVER_TEST")).thenReturn(driverPlan);when(plans.get("TENANT_TEST")).thenReturn(tenantPlan);when(plans.all()).thenReturn(List.of(driverPlan,tenantPlan));
  when(provider.inspectToken(anyString())).thenAnswer(i->new PaymentProvider.TokenDetails(i.getArgument(0),"Visa","4242",12,2035));
  when(provider.charge(any(),anyInt(),anyString(),any(),anyString())).thenAnswer(i->new PaymentProvider.PaymentResult(outcome.get(),"test-op-"+i.getArgument(0)+"-"+i.getArgument(1),outcome.get()==PaymentStatus.REJECTED?"DECLINED":null));
  when(provider.reconcile(any(),anyInt(),any())).thenAnswer(i->new PaymentProvider.PaymentResult(outcome.get(),"test-op-"+i.getArgument(0)+"-"+i.getArgument(1),null));
  when(provider.refund(any(),anyString(),any(),anyString())).thenAnswer(i->new PaymentProvider.RefundResult(RefundStatus.COMPLETED,"test-refund-"+i.getArgument(0)));
  superAuth=register("super-test");var role=roles.findByAccountId(superAuth.accountId()).orElseThrow();role.assign(UserRole.SUPER_ADMIN,null,Instant.EPOCH,null,AssignmentStatus.ACTIVE);roles.saveAndFlush(role);superAdmin=actor(superAuth);
  tenant=parking.createTenant(superAdmin,"Demo","Demo",Map.of("latitude",0,"longitude",0),Map.of(),rules());
  adminAuth=register("admin-test");role=roles.findByAccountId(adminAuth.accountId()).orElseThrow();role.assign(UserRole.PARKING_ADMIN,tenant.getId(),Instant.EPOCH,null,AssignmentStatus.ACTIVE);roles.saveAndFlush(role);admin=actor(adminAuth);adminProfile=profiles.create(admin,"Ana","Demo","900000001");
  driverAuth=register("driver-test");driver=actor(driverAuth);driverProfile=profiles.create(driver,"Juan","Demo","900000002");vehicle=vehicles.saveAndFlush(new Vehicle(driverProfile,"DEMO-1","Kia","Rio","Rojo"));
  otherAuth=register("other-test");other=actor(otherAuth);var op=profiles.create(other,"Sofia","Demo","900000003");otherVehicle=vehicles.saveAndFlush(new Vehicle(op,"DEMO-2","Toyota","Yaris","Azul"));
  var zone=parking.createZone(admin,tenant.getId(),"Piso1-A",Map.of(),"#2563EB");spot=parking.createSpot(admin,zone.getId(),"A-01","STANDARD",Map.of());second=parking.createSpot(admin,zone.getId(),"A-02","STANDARD",Map.of());
 }
 PaymentToken token(SpotgoUserPrincipal who){return billing.registerToken(who,"test-card-"+UUID.randomUUID());}
 Reservation reserve(){return parking.reserve(driver,spot.getId(),vehicle.getId(),now.get().plusSeconds(60),now.get().plusSeconds(5460));}
 DigitalPayment payReservation(Reservation r){
  var t=token(driver);var p=billing.reservationPayment(driver,r.getId(),t.getId(),"reserve-"+r.getId());return billing.process(driver,p.getId(),false);
 }
 AdditionalCharge charge(){
  var r=reserve();payReservation(r);now.set(r.getStartAt());var s=parking.enter(admin,r.getId());now.set(r.getEndAt());parking.exit(admin,s.getId());
  return billing.createCharge(admin,r.getId(),new BigDecimal("5.00"),"DAMAGE","test-evidence-"+r.getId());
 }
 HttpResponse<String> request(String method,String path,Object body,String access) throws Exception{
  var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");if(access!=null)builder.header("Authorization","Bearer "+access);
  builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(new ObjectMapper().findAndRegisterModules().writeValueAsString(body)));
  try(var client=HttpClient.newHttpClient()){return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
 }

 void companySubscription(){var p=billing.subscriptionPayment(admin,"TENANT_TEST",token(admin).getId(),"company");billing.process(admin,p.getId(),false);}
 OccupancySensor sensor(ParkingSpot spot){return monitoring.register(admin,spot.getId(),SensorProtocol.HTTP,Map.of(),now.get().minusSeconds(60),OccupancyStatus.AVAILABLE,now.get(),new BigDecimal("99.00"));}
 OccupancyReading reading(OccupancySensor sensor,OccupancyStatus status){return monitoring.receive(admin,sensor.getId(),status,now.get(),new BigDecimal("99.00"));}
 void advance(long seconds){now.set(now.get().plusSeconds(seconds));}
 @Test void sensorRequiresAnApprovedCompanySubscription(){
  assertThrows(MonitoringException.class,()->sensor(spot));
  var p=billing.subscriptionPayment(admin,"TENANT_TEST",token(admin).getId(),"pending-company");
  assertThrows(MonitoringException.class,()->sensor(spot));
  billing.process(admin,p.getId(),false);assertNotNull(sensor(spot).getId());
 }
 @Test void registeringSensorCreatesRealFirstReadingAndNoExtraColumns(){
  companySubscription();var s=sensor(spot);
  assertEquals(tenant.getId(),s.getTenantId());assertEquals(spot.getZoneId(),s.getZoneId());assertEquals(now.get(),s.getLastSeenAt());
  var first=readingRepository.bySensor(s.getId()).get(0);
  assertEquals(spot.getId(),first.getSpotId());assertEquals(now.get(),first.getReceivedAt());assertEquals(64,first.getPayloadHash().length());
  assertEquals("AVAILABLE",occupancy.physicalOccupancy(spot.getId()));
 }
 @Test void sensorRegistrationDoesNotInventFirstContactOrAcceptFutureDates(){
  companySubscription();
  assertThrows(IllegalArgumentException.class,()->monitoring.register(admin,spot.getId(),SensorProtocol.HTTP,Map.of(),now.get(),OccupancyStatus.AVAILABLE,now.get().plusSeconds(1),BigDecimal.valueOf(99)));
  assertThrows(IllegalArgumentException.class,()->monitoring.register(admin,spot.getId(),SensorProtocol.HTTP,Map.of(),now.get().minusSeconds(1000),OccupancyStatus.AVAILABLE,now.get().minusSeconds(301),BigDecimal.valueOf(99)));
  assertEquals(0,sensorRepository.count());
 }
 @Test void oneActiveSensorPerSpotAndReplacementPreservesHistory(){
  companySubscription();var first=sensor(spot);assertThrows(MonitoringException.class,()->sensor(spot));
  monitoring.deactivate(admin,first.getId());assertEquals("UNAVAILABLE",occupancy.physicalOccupancy(spot.getId()));
  var replacement=sensor(spot);assertNotEquals(first.getId(),replacement.getId());assertEquals(2,sensorRepository.count());
  assertThrows(MonitoringException.class,()->reading(first,OccupancyStatus.AVAILABLE));
 }
 @Test void planSensorLimitIsEnforced(){
  when(plans.get("TENANT_TEST")).thenReturn(Map.of("code","TENANT_TEST","audience","PARKING_ADMIN","price",50,"billing_period_months",1,"included_sensors",1));
  companySubscription();sensor(spot);assertThrows(MonitoringException.class,()->sensor(second));assertEquals(1,sensorRepository.count());
 }
 @Test void expiredCompanySubscriptionMakesObservationUnavailable(){
  companySubscription();var s=sensor(spot);now.set(now.get().atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
  reading(s,OccupancyStatus.AVAILABLE);assertEquals("UNAVAILABLE",occupancy.physicalOccupancy(spot.getId()));
  assertThrows(MonitoringException.class,()->sensor(second));
 }
 @Test void mqttRegistrationRequiresTopicButUsesTheSameAuthenticatedIngestion(){
  companySubscription();
  assertThrows(IllegalArgumentException.class,()->monitoring.register(admin,spot.getId(),SensorProtocol.MQTT,Map.of(),now.get().minusSeconds(60),OccupancyStatus.AVAILABLE,now.get(),BigDecimal.valueOf(99)));
  var s=monitoring.register(admin,spot.getId(),SensorProtocol.MQTT,Map.of("topic","spotgo/demo"),now.get().minusSeconds(60),OccupancyStatus.AVAILABLE,now.get(),BigDecimal.valueOf(99));
  advance(1);assertNotNull(reading(s,OccupancyStatus.OCCUPIED).getId());
 }
 @Test void duplicateMessagesDoNotDuplicateReadingsConflictsOrAlerts(){
  companySubscription();var s=sensor(spot);advance(1);var r=reading(s,OccupancyStatus.OCCUPIED);
  assertEquals(r.getId(),reading(s,OccupancyStatus.OCCUPIED).getId());assertEquals(2,readingRepository.count());assertEquals(1,conflictRepository.count());assertEquals(1,alertRepository.count());
  var c=conflictRepository.findAll().get(0);assertNull(c.getReservationId());assertEquals(ConflictType.UNEXPECTED_OCCUPANCY,c.getConflictType());
  assertEquals(r.getId(),c.getReadingId());assertEquals(tenant.getId(),c.getTenantId());
 }
 @Test void sameIncidentUsesOneOpenConflictAndRecoversWithNewReliableReading(){
  companySubscription();var s=sensor(spot);advance(1);reading(s,OccupancyStatus.OCCUPIED);advance(1);reading(s,OccupancyStatus.OCCUPIED);
  assertEquals(1,conflictRepository.count());advance(1);reading(s,OccupancyStatus.AVAILABLE);
  assertEquals(ConflictStatus.RESOLVED,conflictRepository.findAll().get(0).getStatus());assertEquals(AlertStatus.CLOSED,alertRepository.findAll().get(0).getStatus());
 }
 @Test void differentPayloadAtSameSensorInstantIsRejected(){
  companySubscription();var s=sensor(spot);assertThrows(MonitoringException.class,()->reading(s,OccupancyStatus.OCCUPIED));assertEquals(1,readingRepository.count());
 }
 @Test void lateAndLowQualityMeasurementsDoNotFalsifyCurrentOccupancy(){
  companySubscription();var s=sensor(spot);advance(10);reading(s,OccupancyStatus.AVAILABLE);
  monitoring.receive(admin,s.getId(),OccupancyStatus.OCCUPIED,now.get().minusSeconds(5),BigDecimal.valueOf(99));
  assertEquals("AVAILABLE",occupancy.physicalOccupancy(spot.getId()));assertEquals(0,conflictRepository.count());
  advance(1);monitoring.receive(admin,s.getId(),OccupancyStatus.OCCUPIED,now.get(),BigDecimal.valueOf(10));
  assertEquals("UNAVAILABLE",occupancy.physicalOccupancy(spot.getId()));assertEquals(0,conflictRepository.count());assertEquals(1,healthRepository.count());
  advance(1);reading(s,OccupancyStatus.AVAILABLE);assertEquals("AVAILABLE",occupancy.physicalOccupancy(spot.getId()));
  assertNotNull(healthRepository.findAll().get(0).getRecoveredAt());
 }
 @Test void sensorFailureCreatesHealthAlertAndNoOccupancyConflict(){
  companySubscription();var s=sensor(spot);var event=monitoring.recordHealth(admin,s.getId(),SensorHealth.FAILED,"Hardware diagnostic");
  var alert=alertRepository.findAll().get(0);assertEquals(event.getId(),alert.getHealthEventId());assertNull(alert.getConflictId());assertEquals(0,conflictRepository.count());
  assertEquals("UNAVAILABLE",occupancy.physicalOccupancy(spot.getId()));assertNull(event.getRecoveredAt());
  assertThrows(MonitoringException.class,()->monitoring.updateAlert(admin,alert.getId(),true));
  var ack=monitoring.updateAlert(admin,alert.getId(),false);assertNotNull(ack.getAcknowledgedAt());
  monitoring.recoverHealth(admin,event.getId());assertNotNull(healthRepository.findById(event.getId()).orElseThrow().getRecoveredAt());
  assertEquals(AlertStatus.CLOSED,alertRepository.findById(alert.getId()).orElseThrow().getStatus());
 }
 @Test void timeoutIsDeduplicatedAndFreshCommunicationRecoversEpisode(){
  companySubscription();var s=sensor(spot);advance(301);monitoring.checkHealth();monitoring.checkHealth();
  assertEquals(1,healthRepository.count());assertEquals(1,alertRepository.count());assertEquals(0,conflictRepository.count());
  assertEquals("UNAVAILABLE",occupancy.physicalOccupancy(spot.getId()));
  advance(1);reading(s,OccupancyStatus.AVAILABLE);assertNotNull(healthRepository.findAll().get(0).getRecoveredAt());
  assertEquals("AVAILABLE",occupancy.physicalOccupancy(spot.getId()));
 }
 @Test void replayOfOldMessageDoesNotKeepDeadSensorAlive(){
  companySubscription();var s=sensor(spot);var first=readingRepository.bySensor(s.getId()).get(0);advance(301);monitoring.checkHealth();
  monitoring.receive(admin,s.getId(),first.getOccupancyStatus(),first.getOccurredAt(),first.getQuality());
  assertEquals("UNAVAILABLE",occupancy.physicalOccupancy(spot.getId()));assertNull(healthRepository.findAll().get(0).getRecoveredAt());
 }
 @Test void paidReservationWithoutValidStayCanProduceAConflictWithReservationId(){
  companySubscription();var s=sensor(spot);var r=reserve();payReservation(r);now.set(r.getStartAt());reading(s,OccupancyStatus.OCCUPIED);
  var c=conflictRepository.findAll().get(0);assertEquals(r.getId(),c.getReservationId());assertEquals(ConflictType.SPACE_NOT_AVAILABLE,c.getConflictType());
 }
 @Test void validGuestStayDoesNotCreateUnexpectedOccupancy(){
  companySubscription();var s=sensor(spot);
  parking.guestEnter(admin,spot.getId(),"GUEST-1",com.yachiqo.spotgo.parking.domain.model.valueobjects.PhysicalPaymentMethod.CASH);
  advance(1);reading(s,OccupancyStatus.OCCUPIED);assertEquals(0,conflictRepository.count());
 }
 @Test void validReservationStayDoesNotConflictButPhysicalAbsenceDoes(){
  companySubscription();var s=sensor(spot);var r=reserve();payReservation(r);now.set(r.getStartAt());parking.enter(admin,r.getId());
  advance(1);reading(s,OccupancyStatus.OCCUPIED);assertEquals(0,conflictRepository.count());
  advance(1);reading(s,OccupancyStatus.AVAILABLE);assertEquals(ConflictType.EXPECTED_OCCUPANCY_MISSING,conflictRepository.findAll().get(0).getConflictType());
 }
 @Test void fiveContextsConnectOverstayToConflictChargeDebtAndPayment(){
  companySubscription();var s=sensor(spot);var r=reserve();payReservation(r);now.set(r.getStartAt());var stay=parking.enter(admin,r.getId());
  now.set(r.getEndAt().plusSeconds(301));reading(s,OccupancyStatus.OCCUPIED);
  assertEquals(ConflictType.OVERSTAY,conflictRepository.findAll().get(0).getConflictType());
  parking.exit(admin,stay.getId());var charge=charges.byReservation(r.getId()).get(0);
  assertEquals(driverProfile.getId(),balances.byCharge(charge.getId()).orElseThrow().getProfileId());assertThrows(BillingException.class,this::reserve);
  var payment=billing.chargePayment(driver,charge.getId(),token(driver).getId(),"overstay-debt");billing.process(driver,payment.getId(),false);
  assertEquals(OutstandingBalanceStatus.REGULARIZED,balances.byCharge(charge.getId()).orElseThrow().getStatus());assertNotNull(reserve().getId());
 }
 @Test void availabilityUsesSensorStateAndDistinguishesFutureSlots(){
  companySubscription();var s=sensor(spot);
  assertTrue(parkingQueries.availability(spot.getZoneId(),now.get(),now.get().plusSeconds(60)).stream().filter(a->a.spotId().equals(spot.getId())).findFirst().orElseThrow().bookable());
  advance(1);reading(s,OccupancyStatus.OCCUPIED);
  var current=parkingQueries.availability(spot.getZoneId(),now.get(),now.get().plusSeconds(60)).stream().filter(a->a.spotId().equals(spot.getId())).findFirst().orElseThrow();
  assertFalse(current.bookable());assertEquals("OCCUPIED",current.physicalOccupancy());
  assertThrows(com.yachiqo.spotgo.parking.application.ParkingException.class,()->parking.reserve(driver,spot.getId(),vehicle.getId(),now.get(),now.get().plusSeconds(60)));
  assertNotNull(reserve().getId());
 }
 @Test void snapshotsCountLatestReliableReadingPerOperationalSpot(){
  companySubscription();var a=sensor(spot);sensor(second);advance(1);reading(a,OccupancyStatus.OCCUPIED);
  var snap=monitoring.snapshot(admin,tenant.getId(),spot.getZoneId());assertEquals(1,snap.getOccupiedCount());assertEquals(2,snap.getTotalCount());assertEquals(new BigDecimal("50.00"),snap.getPercentage());
  var global=monitoring.snapshot(admin,tenant.getId(),null);assertNull(global.getZoneId());assertEquals(2,global.getTotalCount());
 }
 @Test void unknownSpotDoesNotCountAsFreeAndDoesNotCreatePartialSnapshot(){
  companySubscription();sensor(spot);long before=snapshotRepository.count();
  assertThrows(MonitoringException.class,()->monitoring.snapshot(admin,tenant.getId(),spot.getZoneId()));
  assertEquals(before,snapshotRepository.count());
 }
 @Test void zeroOperationalSpacesHasZeroPercentWithoutDivisionByZero(){
  companySubscription();var z=parking.createZone(admin,tenant.getId(),"Empty",Map.of(),"#000000");
  var snap=monitoring.snapshot(admin,tenant.getId(),z.getId());assertEquals(0,snap.getTotalCount());assertEquals(new BigDecimal("0.00"),snap.getPercentage());
 }
 @Test void reportsAggregateZoneSnapshotsAndDoNotPointToOneSnapshot(){
  companySubscription();var a=sensor(spot);sensor(second);var start=now.get();advance(1);reading(a,OccupancyStatus.OCCUPIED);advance(1);
  var report=monitoring.report(admin,spot.getZoneId(),start,now.get());
  assertEquals(spot.getZoneId(),report.getZoneId());assertEquals("50.00",report.getTotals().get("peak_occupancy_percentage").toString());assertEquals(1L,report.getTotals().get("alerts_count"));
  assertThrows(MonitoringException.class,()->monitoring.report(admin,spot.getZoneId(),start.minusSeconds(20),start.minusSeconds(1)));
  assertThrows(IllegalArgumentException.class,()->monitoring.report(admin,spot.getZoneId(),start,now.get().plusSeconds(1)));
 }
 @Test void alertOriginIsExclusiveAndDatabaseHasRealForeignKeys(){
  companySubscription();var s=sensor(spot);advance(1);reading(s,OccupancyStatus.OCCUPIED);var event=monitoring.recordHealth(admin,s.getId(),SensorHealth.FAILED,"Manual diagnostic");
  var conflict=conflictRepository.findAll().get(0);var alert=alertRepository.findAll().stream().filter(a->a.getConflictId()!=null).findFirst().orElseThrow();
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE occupancy_alerts SET health_event_id=? WHERE alert_id=?",event.getId(),alert.getId()));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE occupancy_alerts SET conflict_id=NULL WHERE alert_id=?",alert.getId()));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE occupancy_conflicts SET reading_id=? WHERE conflict_id=?",UUID.randomUUID(),conflict.getId()));
 }
 @Test void driversAndOtherTenantAdministratorsCannotReadOrMutateMonitoring() throws Exception {
  companySubscription();var s=sensor(spot);assertThrows(AccessDeniedException.class,()->monitoring.readings(driver,s.getId()));
  assertEquals(403,request("GET","/api/v1/occupancy-sensors",null,driverAuth.accessToken()).statusCode());
  var otherTenant=parking.createTenant(superAdmin,"Other","Other",Map.of("latitude",0,"longitude",0),Map.of(),rules());
  var assignment=roles.findByAccountId(otherAuth.accountId()).orElseThrow();assignment.assign(UserRole.PARKING_ADMIN,otherTenant.getId(),Instant.EPOCH,null,AssignmentStatus.ACTIVE);roles.saveAndFlush(assignment);other=actor(otherAuth);
  assertThrows(AccessDeniedException.class,()->monitoring.sensor(other,s.getId()));
  assertThrows(AccessDeniedException.class,()->monitoring.recordHealth(other,s.getId(),SensorHealth.FAILED,"not mine"));
  assertEquals(1,monitoring.sensors(superAdmin,null).size());
 }
 @Test void swaggerContainsAllSevenCurrentMonitoringResourcesAndNoOldEndpoints() throws Exception {
  var response=request("GET","/v3/api-docs/04-backend-completo",null,null);assertEquals(200,response.statusCode());var paths=new ObjectMapper().readTree(response.body()).get("paths");
  for(var path:List.of("occupancy-sensors","occupancy-readings","sensor-health-events","occupancy-conflicts","occupancy-alerts","capacity-snapshots","occupancy-reports"))assertTrue(paths.has("/api/v1/"+path),path);
  for(var path:List.of("employees","occupancyByHour","weeklyTrends","analytics","receipts","clientPlans"))assertFalse(paths.has("/api/v1/"+path),path);
 }
 @Test void seederLoadsAllThirtyOneTablesAndTheirMonitoringRelationships() throws Exception {
  new com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.seeder.DbSeeder(jdbc,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()).run();
  for(String table:List.of("occupancy_sensors","occupancy_readings","sensor_health_events","occupancy_conflicts","occupancy_alerts","capacity_snapshots","occupancy_reports"))assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)>0,table);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM occupancy_alerts a JOIN sensor_health_events h ON a.health_event_id=h.health_event_id JOIN occupancy_sensors s ON h.sensor_id=s.sensor_id WHERE a.tenant_id=s.tenant_id AND a.spot_id=s.spot_id",Integer.class));
 }

 com.fasterxml.jackson.databind.JsonNode journeyJson(String method,String path,Object body,String token,int expected) throws Exception {
  var response=request(method,path,body,token);
  assertEquals(expected,response.statusCode(),method+" "+path+": "+response.body());
  return new ObjectMapper().readTree(response.body());
 }
 @Test void completeHttpCompanySubscriptionSensorReadingAndAlertJourney() throws Exception {
  String access=adminAuth.accessToken();
  var sensorBody=Map.of("spotId",spot.getId(),"protocol","HTTP","configuration",Map.of(),"installedAt",now.get().minusSeconds(60).toString(),"firstReading",Map.of("occupancyStatus","AVAILABLE","occurredAt",now.get().toString(),"quality",99));
  assertEquals(409,request("POST","/api/v1/occupancy-sensors",sensorBody,access).statusCode());
  var card=journeyJson("POST","/api/v1/payment-tokens",Map.of("providerTokenRef","company-journey-card"),access,201);
  var payment=journeyJson("POST","/api/v1/digital-payments/subscriptions",Map.of("planCode","TENANT_TEST","tokenId",card.path("tokenId").asText(),"idempotencyKey","company-journey"),access,201);
  assertTrue(journeyJson("GET","/api/v1/subscriptions",null,access,200).isEmpty());
  journeyJson("POST","/api/v1/digital-payments/"+payment.path("paymentId").asText()+"/process",null,access,200);
  var subscription=journeyJson("GET","/api/v1/subscriptions",null,access,200).get(0);
  assertEquals("ACTIVE",subscription.path("status").asText());assertEquals(tenant.getId().toString(),subscription.path("tenantId").asText());
  assertTrue(subscription.path("profileId").isNull());assertEquals(payment.path("paymentId"),subscription.path("paymentId"));
  var sensor=journeyJson("POST","/api/v1/occupancy-sensors",sensorBody,access,201);
  assertEquals(spot.getId().toString(),sensor.path("spotId").asText());assertEquals(tenant.getId().toString(),sensor.path("tenantId").asText());
  advance(1);
  var reading=journeyJson("POST","/api/v1/occupancy-readings",Map.of("sensorId",sensor.path("sensorId").asText(),"occupancyStatus","OCCUPIED","occurredAt",now.get().toString(),"quality",99),access,201);
  var conflicts=journeyJson("GET","/api/v1/occupancy-conflicts",null,access,200);assertEquals(1,conflicts.size());
  var conflict=conflicts.get(0);assertEquals(reading.path("readingId"),conflict.path("readingId"));assertTrue(conflict.path("reservationId").isNull());
  var alerts=journeyJson("GET","/api/v1/occupancy-alerts",null,access,200);assertEquals(1,alerts.size());var alert=alerts.get(0);
  assertEquals(conflict.path("conflictId"),alert.path("conflictId"));assertTrue(alert.path("healthEventId").isNull());assertTrue(alert.path("evidenceRef").isNull());
  assertEquals(spot.getId().toString(),alert.path("spotId").asText());assertEquals(tenant.getId().toString(),alert.path("tenantId").asText());
  assertEquals(403,request("GET","/api/v1/occupancy-alerts",null,driverAuth.accessToken()).statusCode());
  journeyJson("POST","/api/v1/occupancy-alerts/"+alert.path("alertId").asText()+"/acknowledge",null,access,200);
  assertEquals("ACKNOWLEDGED",journeyJson("GET","/api/v1/occupancy-alerts",null,access,200).get(0).path("status").asText());
 }
 @Test void authenticatedHttpFlowReadsAllSevenResourcesWithoutLazySerializationErrors() throws Exception {
  companySubscription();
  var sensorResponse=request("POST","/api/v1/occupancy-sensors",Map.of("spotId",spot.getId(),"protocol","HTTP","configuration",Map.of(),"installedAt",now.get().minusSeconds(60).toString(),"firstReading",Map.of("occupancyStatus","AVAILABLE","occurredAt",now.get().toString(),"quality",99)),adminAuth.accessToken());
  assertEquals(201,sensorResponse.statusCode());var id=new ObjectMapper().readTree(sensorResponse.body()).path("sensorId").asText();
  advance(1);
  assertEquals(201,request("POST","/api/v1/occupancy-readings",Map.of("sensorId",id,"occupancyStatus","OCCUPIED","occurredAt",now.get().toString(),"quality",99),adminAuth.accessToken()).statusCode());
  for(var path:List.of("/occupancy-sensors","/occupancy-readings?sensorId="+id,"/sensor-health-events?sensorId="+id,"/occupancy-conflicts","/occupancy-alerts","/capacity-snapshots","/occupancy-reports")){
   var response=request("GET","/api/v1"+path,null,adminAuth.accessToken());assertEquals(200,response.statusCode(),path+": "+response.body());
  }
  assertEquals(401,request("POST","/api/v1/occupancy-readings",Map.of("sensorId",id,"occupancyStatus","OCCUPIED","occurredAt",now.get().toString(),"quality",99),null).statusCode());
  assertEquals(404,request("GET","/api/v1/employees",null,adminAuth.accessToken()).statusCode());
 }
 @Test void concurrentRegistrationCannotCreateTwoActiveSensorsForOneSpot() throws Exception {
  companySubscription();var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> create=()->{gate.await();try{sensor(spot);return true;}catch(MonitoringException e){return false;}};
   var one=pool.submit(create);var two=pool.submit(create);gate.countDown();
   int successful=(one.get(10,TimeUnit.SECONDS)?1:0)+(two.get(10,TimeUnit.SECONDS)?1:0);
   assertEquals(1,successful);assertEquals(1,sensorRepository.activeSpot(spot.getId()).size());
  }
 }
 @Test void staleMeasurementMessagesDoNotRecoverHealthWithoutFreshData(){
  companySubscription();var s=sensor(spot);advance(400);
  monitoring.receive(admin,s.getId(),OccupancyStatus.AVAILABLE,now.get().minusSeconds(350),BigDecimal.valueOf(99));
  monitoring.checkHealth();assertEquals("UNAVAILABLE",occupancy.physicalOccupancy(spot.getId()));
  assertTrue(healthRepository.bySensor(s.getId()).stream().anyMatch(e->e.getRecoveredAt()==null&&e.getReason().startsWith("STALE_MEASUREMENT")));
  advance(1);reading(s,OccupancyStatus.AVAILABLE);assertEquals("AVAILABLE",occupancy.physicalOccupancy(spot.getId()));
 }

 static byte[] photo() throws Exception {
  var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
  image.setRGB(0,0,0x123456);
  try(var out=new java.io.ByteArrayOutputStream()){javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();}
 }
 HttpResponse<byte[]> upload(UUID alert,String token,byte[] bytes,String filename) throws Exception {
  String boundary="SpotGoBoundary"+UUID.randomUUID();
  try(var out=new java.io.ByteArrayOutputStream()){
   out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"image\"; filename=\""+filename+"\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
   out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/occupancy-alerts/"+alert+"/evidence"))
    .header("Content-Type","multipart/form-data; boundary="+boundary).PUT(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
   if(token!=null)builder.header("Authorization","Bearer "+token);
   try(var client=HttpClient.newHttpClient()){return client.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());}
  }
 }
 HttpResponse<byte[]> photoRequest(String path,String token) throws Exception {
  var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET();
  if(token!=null)builder.header("Authorization","Bearer "+token);
  try(var client=HttpClient.newHttpClient()){return client.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());}
 }
 @Test void actualImageFileIsUploadedStoredAndReturnedWithImageContentType() throws Exception {
  companySubscription();var sensor=sensor(spot);advance(1);reading(sensor,OccupancyStatus.OCCUPIED);
  var alert=alertRepository.findAll().get(0);assertNull(alert.getEvidenceRef());byte[] image=photo();
  var response=upload(alert.getId(),adminAuth.accessToken(),image,"../../client-photo.png");
  assertEquals(200,response.statusCode(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8));
  var saved=alertRepository.findById(alert.getId()).orElseThrow();assertEquals(alert.getConflictId(),saved.getConflictId());
  assertTrue(saved.getEvidenceRef().startsWith("/api/v1/occupancy-alerts/"+alert.getId()+"/evidence/"));
  assertFalse(saved.getEvidenceRef().contains("client-photo"));
  var downloaded=photoRequest("/api/v1/occupancy-alerts/"+alert.getId()+"/evidence",adminAuth.accessToken());
  assertEquals(200,downloaded.statusCode());assertEquals("image/png",downloaded.headers().firstValue("Content-Type").orElseThrow());assertArrayEquals(image,downloaded.body());
  assertEquals(200,photoRequest("/api/v1/occupancy-alerts/"+alert.getId()+"/evidence",superAuth.accessToken()).statusCode());
  assertEquals(403,photoRequest("/api/v1/occupancy-alerts/"+alert.getId()+"/evidence",driverAuth.accessToken()).statusCode());
  assertEquals(401,photoRequest("/api/v1/occupancy-alerts/"+alert.getId()+"/evidence",null).statusCode());
  assertEquals(403,upload(alert.getId(),driverAuth.accessToken(),image,"fake.png").statusCode());
  assertEquals(1,alertRepository.count());
 }
 @Test void fakeImagesAndOldJsonUrlInputAreRejectedWithoutSavingEvidence() throws Exception {
  companySubscription();var sensor=sensor(spot);monitoring.recordHealth(admin,sensor.getId(),SensorHealth.FAILED,"Device fault");
  var alert=alertRepository.findAll().get(0);assertNull(alert.getEvidenceRef());
  assertEquals(415,upload(alert.getId(),adminAuth.accessToken(),"<html>not a photo</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8),"fake.png").statusCode());
  assertEquals(400,upload(alert.getId(),adminAuth.accessToken(),new byte[0],"empty.png").statusCode());
  assertEquals(415,request("PUT","/api/v1/occupancy-alerts/"+alert.getId()+"/evidence",Map.of("evidenceRef","https://example.com/photo.jpg"),adminAuth.accessToken()).statusCode());
  assertNull(alertRepository.findById(alert.getId()).orElseThrow().getEvidenceRef());
  assertEquals(404,photoRequest("/api/v1/occupancy-alerts/"+alert.getId()+"/evidence",adminAuth.accessToken()).statusCode());
 }
 @Test void replacingPhotoRemovesPreviousFileAfterCommitAndRollbackRemovesNewUpload() throws Exception {
  companySubscription();var sensor=sensor(spot);monitoring.recordHealth(admin,sensor.getId(),SensorHealth.FAILED,"Device fault");
  var alert=alertRepository.findAll().get(0);
  var first=monitoring.uploadEvidence(admin,alert.getId(),photo());String old=first.getEvidenceRef();
  var secondPhoto=monitoring.uploadEvidence(admin,alert.getId(),photo());assertNotEquals(old,secondPhoto.getEvidenceRef());
  assertEquals(404,photoRequest(old,adminAuth.accessToken()).statusCode());
  assertEquals(200,photoRequest("/api/v1/occupancy-alerts/"+alert.getId()+"/evidence",adminAuth.accessToken()).statusCode());
  long before;
  try(var files=java.nio.file.Files.list(evidenceDirectory)){before=files.count();}
  new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status->{
   try{monitoring.uploadEvidence(admin,alert.getId(),photo());}catch(Exception e){throw new RuntimeException(e);}
   status.setRollbackOnly();return null;
  });
  try(var files=java.nio.file.Files.list(evidenceDirectory)){assertEquals(before,files.count());}
  assertEquals(secondPhoto.getEvidenceRef(),alertRepository.findById(alert.getId()).orElseThrow().getEvidenceRef());
 }
 @Test void imageUploadAndDownloadRespectTenantOwnership() throws Exception {
  companySubscription();var sensor=sensor(spot);monitoring.recordHealth(admin,sensor.getId(),SensorHealth.FAILED,"Device fault");
  var alert=alertRepository.findAll().get(0);var withPhoto=monitoring.uploadEvidence(admin,alert.getId(),photo());
  var t=parking.createTenant(superAdmin,"Other","Other",Map.of("latitude",0,"longitude",0),Map.of(),rules());
  var assignment=roles.findByAccountId(otherAuth.accountId()).orElseThrow();assignment.assign(UserRole.PARKING_ADMIN,t.getId(),Instant.EPOCH,null,AssignmentStatus.ACTIVE);roles.saveAndFlush(assignment);
  assertEquals(403,upload(alert.getId(),otherAuth.accessToken(),photo(),"other.png").statusCode());
  assertEquals(403,photoRequest("/api/v1/occupancy-alerts/"+alert.getId()+"/evidence",otherAuth.accessToken()).statusCode());
 }

}
