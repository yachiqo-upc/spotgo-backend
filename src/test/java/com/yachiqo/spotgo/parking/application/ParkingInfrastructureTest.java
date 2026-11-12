package com.yachiqo.spotgo.parking.application;
import com.yachiqo.spotgo.iam.application.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.RoleAssignmentRepository;
import com.yachiqo.spotgo.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.*;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandServiceImpl;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryServiceImpl;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.*;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:parking-integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE,HOUR,ROWNUM,DAY",
 "app.parking.lock-cleanup-ms=3600000"
})
@ActiveProfiles("test")
class ParkingInfrastructureTest {
 @Autowired ParkingCommandServiceImpl commands;
 @Autowired ParkingQueryServiceImpl queries;
 @Autowired AuthenticationService iam;
 @Autowired JwtTokenService jwt;
 @Autowired SessionAuthenticationService authentication;
 @Autowired RoleAssignmentRepository roles;
 @Autowired ProfilesManagementService profiles;
 @Autowired VehicleRepository vehicles;
 @Autowired ReservationRepository reservations;
 @Autowired ParkingSessionRepository sessions;
 @Autowired DigitalParkingMapRepository maps;
 @Autowired ReservationLifecycleService lifecycle;
 @Autowired JdbcTemplate jdbc;
 @LocalServerPort int port;
 @MockitoBean Clock clock;
 @MockitoBean PasswordResetNotificationService notifications;
 AtomicReference<Instant> now=new AtomicReference<>();
 ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 AuthenticatedUserResource driverAuth,adminAuth,otherAuth,superAuth;
 SpotgoUserPrincipal driver,admin,other,superAdmin;
 Tenant tenant;
 ParkingZone zone;
 ParkingSpot spot,second;
 Vehicle vehicle,otherVehicle;
 Map<String,Object> rules(){return Map.of("tariff_rules",Map.of("period_minutes",60,"price_per_period",5,"rounding","CEILING"),
  "overstay_tolerance_minutes",5,"lock_duration_minutes",10,"allocation_rules",Map.of(),"coexistence_rules",List.of("Keep exits clear"));}
 SpotgoUserPrincipal principal(AuthenticatedUserResource auth){return authentication.authenticate(jwt.parseAccessToken(auth.accessToken()));}
 AuthenticatedUserResource register(String name){return iam.signUp(name+"@example.com","Password123!","TEST");}
 @BeforeEach void setup(){
  now.set(Instant.parse("2030-01-01T10:00:00Z"));when(clock.instant()).thenAnswer(i->now.get());when(clock.getZone()).thenReturn(ZoneOffset.UTC);

  for(String table:List.of("favorite_tenants","capacity_alerts","occupancy_alerts","occupancy_reports","capacity_snapshots","occupancy_conflicts","sensor_health_events","occupancy_readings","occupancy_sensors","billing_documents", "refunds","reservation_changes", "outstanding_balances", "additional_charges", "subscriptions", "payment_attempts", "parking_sessions","guest_parking_sessions","reservations","digital_parking_maps","floor_plans","parking_spots","parking_zones",
   "driver_classifications","digital_payments","audit_entries","sessions","credentials","role_assignments","passwordResetCodes","tenants","payment_tokens","vehicles","profiles","accounts")) jdbc.update("DELETE FROM "+table);
  superAuth=register("super");
  var sr=roles.findByAccountId(superAuth.accountId()).orElseThrow();sr.assign(UserRole.SUPER_ADMIN,null,Instant.EPOCH,null,AssignmentStatus.ACTIVE);roles.saveAndFlush(sr);
  superAdmin=principal(superAuth);
  tenant=commands.createTenant(superAdmin,"Parking Demo","Av Demo",Map.of("latitude",-12,"longitude",-77),Map.of("email","parking@example.com"),rules());
  adminAuth=register("admin");
  var ar=roles.findByAccountId(adminAuth.accountId()).orElseThrow();ar.assign(UserRole.PARKING_ADMIN,tenant.getId(),Instant.EPOCH,null,AssignmentStatus.ACTIVE);roles.saveAndFlush(ar);
  admin=principal(adminAuth);profiles.create(admin,"Ana","Demo","900000001");
  driverAuth=register("driver");driver=principal(driverAuth);
  var p=profiles.create(driver,"Juan","Demo","900000002");
  vehicle=vehicles.saveAndFlush(new Vehicle(p,"DEMO-1","Kia","Rio","Red"));
  otherAuth=register("other");other=principal(otherAuth);
  var op=profiles.create(other,"Sofia","Demo","900000003");
  otherVehicle=vehicles.saveAndFlush(new Vehicle(op,"DEMO-2","Toyota","Yaris","Blue"));
  zone=commands.createZone(admin,tenant.getId(),"Floor1-A",Map.of("reference","North"),"#2563EB");
  spot=commands.createSpot(admin,zone.getId(),"A-01","STANDARD",Map.of("covered",true));
  second=commands.createSpot(admin,zone.getId(),"A-02","STANDARD",Map.of());
 }
 Reservation reserve(){return commands.reserve(driver,spot.getId(),vehicle.getId(),now.get().plusSeconds(60),now.get().plusSeconds(5460));}
 void payment(UUID id,UUID profile,BigDecimal amount,String status){

    jdbc.update("INSERT INTO digital_payments(payment_id,profile_id,amount,status,operation_type,currency,idempotency_key,request_hash,first_seen_at,last_seen_at,result_reference,created_at) VALUES (?,?,?,?,?,'PEN',?,'fixture',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'fixture',CURRENT_TIMESTAMP)",id,profile,amount,status,"RESERVATION",id.toString());
  jdbc.update("UPDATE reservations SET payment_id=? WHERE payment_id is null AND profile_id=? AND status='PENDING_PAYMENT'",id,vehicle.getProfileId());
 }
 HttpResponse<String> request(String method,String path,Object body,String token) throws Exception {
  var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
  if(token!=null)builder.header("Authorization","Bearer "+token);
  builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  try(var client=HttpClient.newHttpClient()){return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
 }
 @Test void seederLoadsNewParkingTablesAndJsonRules() throws Exception {
  new com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.seeder.DbSeeder(jdbc,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()).run();
  var seeded=queries.tenant(UUID.fromString("00000000-0000-4000-8000-000000000021"));
  assertTrue(seeded.getRules().get("tariff_rules") instanceof Map);
  var demoTenantId=UUID.fromString("00000000-0000-4000-8000-000000000022");
  assertEquals("Miraflores Parking",queries.tenant(demoTenantId).getName());
  var demoZoneId=UUID.fromString("00000000-0000-4000-8000-000000000033");
  assertEquals(2,queries.spots(driver,demoZoneId).size());
  var seededMap=maps.findById(UUID.fromString("00000000-0000-4000-8000-000000000151")).orElseThrow();
  assertEquals(2,seededMap.getElements().size());
  assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM guest_parking_sessions WHERE guest_session_id=?",String.class,UUID.fromString("00000000-0000-4000-8000-000000000171")));
 }
 @Test void nativeUuidTablesAndRelationshipsArePresent(){
  for(String table:List.of("tenants","parking_zones","parking_spots","floor_plans","digital_parking_maps","reservations","parking_sessions","guest_parking_sessions"))
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name)=?",Integer.class,table));
  UUID zoneTenant=jdbc.queryForObject("SELECT tenant_id FROM parking_zones WHERE zone_id=?",UUID.class,zone.getId());assertEquals(tenant.getId(),zoneTenant);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE parking_zones SET tenant_id=? WHERE zone_id=?",UUID.randomUUID(),zone.getId()));
 }
 @Test void createsPendingReservationWithServerIdentityAndCeilingPrice(){
  var r=reserve();assertEquals(ReservationStatus.PENDING_PAYMENT,r.getStatus());assertNull(r.getPaymentId());
  assertEquals(vehicle.getProfileId(),r.getProfileId());assertEquals(tenant.getId(),r.getTenantId());
  assertEquals(new BigDecimal("10.00"),r.getAmount());assertEquals(now.get().plusSeconds(600),r.getLockExpiresAt());
 }
 @Test void ownershipAndTenantScopesAreEnforced(){
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->commands.reserve(other,spot.getId(),vehicle.getId(),now.get().plusSeconds(60),now.get().plusSeconds(3600)));
  var t2=commands.createTenant(superAdmin,"Otro","Demo",Map.of("latitude",0,"longitude",0),Map.of(),rules());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->commands.createZone(admin,t2.getId(),"X",Map.of(),"#000000"));
  var r=reserve();assertThrows(org.springframework.security.access.AccessDeniedException.class,()->queries.reservation(other,r.getId()));
 }
 @Test void rejectsOverlapButAllowsAdjacentIntervals(){
  var r=reserve();
  assertThrows(ParkingException.class,()->commands.reserve(other,spot.getId(),otherVehicle.getId(),r.getStartAt(),r.getEndAt()));
  assertThrows(ParkingException.class,()->commands.reserve(driver,second.getId(),vehicle.getId(),r.getStartAt(),r.getEndAt()));
  assertNotNull(commands.reserve(other,spot.getId(),otherVehicle.getId(),r.getEndAt(),r.getEndAt().plusSeconds(3600)).getId());
 }
 @Test void concurrentReservationsCannotBothAcquireSameSpot() throws Exception {
  var from=now.get().plusSeconds(60);var to=from.plusSeconds(3600);
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var gate=new CountDownLatch(1);
   var tasks=List.of(executor.submit(()->{gate.await();try{commands.reserve(driver,spot.getId(),vehicle.getId(),from,to);return true;}catch(ParkingException e){return false;}}),
    executor.submit(()->{gate.await();try{commands.reserve(other,spot.getId(),otherVehicle.getId(),from,to);return true;}catch(ParkingException e){return false;}}));
   gate.countDown();int accepted=0;for(var task:tasks)if(task.get(20,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);
  }
 }
 @Test void lockExpiresAndReleasesAvailability(){
  var r=reserve();now.set(now.get().plusSeconds(601));lifecycle.expireLocks();
  var expired=reservations.findById(r.getId()).orElseThrow();assertEquals(LockStatus.EXPIRED,expired.getLockStatus());assertEquals(ReservationStatus.CANCELLED,expired.getStatus());
  assertNotNull(commands.reserve(other,spot.getId(),otherVehicle.getId(),now.get().plusSeconds(60),now.get().plusSeconds(3660)));
 }
 @Test void cannotConfirmMissingBillingOrUnapprovedPayment(){
  var r=reserve();var id=UUID.randomUUID();
  assertEquals(409,assertThrows(ParkingException.class,()->commands.confirm(driver,r.getId(),id)).getStatusCode().value());
  payment(id,r.getProfileId(),r.getAmount(),"REJECTED");
  assertEquals(409,assertThrows(com.yachiqo.spotgo.billing.application.BillingException.class,()->commands.confirm(driver,r.getId(),id)).getStatusCode().value());
  assertEquals(ReservationStatus.PENDING_PAYMENT,reservations.findById(r.getId()).orElseThrow().getStatus());
 }
 @Test void confirmationRequiresMatchingOwnerAndAmount(){
  var r=reserve();UUID id=UUID.randomUUID();payment(id,otherVehicle.getProfileId(),r.getAmount(),"APPROVED");
  assertThrows(com.yachiqo.spotgo.billing.application.BillingException.class,()->commands.confirm(driver,r.getId(),id));
  jdbc.update("UPDATE digital_payments SET profile_id=?,amount=1 WHERE payment_id=?",r.getProfileId(),id);
  assertThrows(com.yachiqo.spotgo.billing.application.BillingException.class,()->commands.confirm(driver,r.getId(),id));
 }
 @Test void reservationPaymentAndParkingEntryExitFlow(){
  var r=reserve();UUID id=UUID.randomUUID();payment(id,r.getProfileId(),r.getAmount(),"APPROVED");
  assertEquals(ReservationStatus.RESERVED,commands.confirm(driver,r.getId(),id).getStatus());
  now.set(r.getStartAt());var s=commands.enter(admin,r.getId());assertNull(s.getExitAt());
  assertThrows(ParkingException.class,()->commands.enter(admin,r.getId()));
  now.set(r.getEndAt());var done=commands.exit(admin,s.getId());assertEquals(ParkingSessionStatus.COMPLETED,done.getStatus());
  assertEquals(ReservationStatus.COMPLETED,reservations.findById(r.getId()).orElseThrow().getStatus());
 }
 @Test void overstayMarksReservationAndCreatesBillingCharge(){
  var r=reserve();UUID id=UUID.randomUUID();payment(id,r.getProfileId(),r.getAmount(),"APPROVED");commands.confirm(driver,r.getId(),id);
  now.set(r.getStartAt());var s=commands.enter(admin,r.getId());now.set(r.getEndAt().plusSeconds(301));commands.exit(admin,s.getId());
  assertEquals(ReservationStatus.OVERSTAYED,reservations.findById(r.getId()).orElseThrow().getStatus());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM additional_charges WHERE reservation_id=?",Integer.class,r.getId()));
 }
 @Test void cancellationReleasesSpotAndOnlyOwnerOrTenantAdminCanCancel(){
  var r=reserve();assertThrows(org.springframework.security.access.AccessDeniedException.class,()->commands.cancel(other,r.getId()));
  assertEquals(ReservationStatus.CANCELLED,commands.cancel(driver,r.getId()).getStatus());
  assertNotNull(commands.reserve(other,spot.getId(),otherVehicle.getId(),r.getStartAt(),r.getEndAt()));
 }
 @Test void guestFlowHasNoProfileAndCanRegularizePhysicalPayment(){
  var g=commands.guestEnter(admin,spot.getId(),"abc-123",PhysicalPaymentMethod.CASH);
  assertNull(g.getExitAt());assertFalse(g.isPhysicalPaymentConfirmed());assertEquals("ABC-123",g.getManualPlate());
  assertThrows(ParkingException.class,()->commands.guestEnter(admin,spot.getId(),"otro",PhysicalPaymentMethod.POS));
  now.set(now.get().plusSeconds(5400));var exited=commands.guestExit(admin,g.getId(),false);
  assertEquals(new BigDecimal("10.00"),exited.getCalculatedAmount());assertEquals(GuestSessionStatus.PENDING_REVIEW,exited.getStatus());
  assertEquals(GuestSessionStatus.CLOSED,commands.guestPaid(admin,g.getId()).getStatus());
 }
 @Test void inactiveSpotAndSuspendedTenantCannotBeReserved(){
  commands.updateSpot(admin,spot.getId(),"A-01","STANDARD",Map.of(),OperationalStatus.MAINTENANCE);
  assertThrows(ParkingException.class,this::reserve);
  commands.updateSpot(admin,spot.getId(),"A-01","STANDARD",Map.of(),OperationalStatus.ACTIVE);
  commands.updateTenant(admin,tenant.getId(),"Demo","Demo",Map.of("latitude",0,"longitude",0),Map.of(),rules(),TenantStatus.SUSPENDED);
  assertThrows(ParkingException.class,this::reserve);
 }
 @Test void codesAreUniqueWithinZoneAndMayRepeatInOtherZones(){
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->commands.createSpot(admin,zone.getId(),"a-01","STANDARD",Map.of()));
  var z2=commands.createZone(admin,tenant.getId(),"Floor2-A",Map.of(),"#000000");
  assertNotNull(commands.createSpot(admin,z2.getId(),"A-01","STANDARD",Map.of()).getId());
 }
 @Test void floorPlansAndMapsEnforceZoneAndOnePublishedVersion(){
  var p=commands.createPlan(admin,zone.getId(),1,"demo/floor-plan.pdf");assertNull(p.getPublishedAt());
  var m1=commands.createMap(admin,p.getId(),1,List.of(Map.of("type","SPOT","spot_id",spot.getId().toString(),"x",0,"y",0)));
  assertThrows(ParkingException.class,()->commands.publishMap(admin,m1.getId(),false));
  commands.publishPlan(admin,p.getId(),false);commands.publishMap(admin,m1.getId(),false);
  var m2=commands.createMap(admin,p.getId(),2,List.of());commands.publishMap(admin,m2.getId(),false);
  assertEquals(1,maps.byPlan(p.getId()).stream().filter(m->m.getStatus()==PublicationStatus.PUBLISHED).count());
  assertThrows(ParkingException.class,()->commands.createPlan(admin,zone.getId(),1,"otro"));
 }
 @Test void mapCannotReferenceSpotFromAnotherZone(){
  var z2=commands.createZone(admin,tenant.getId(),"Otro",Map.of(),"#000000");var p=commands.createPlan(admin,z2.getId(),1,"demo");
  assertThrows(ParkingException.class,()->commands.createMap(admin,p.getId(),1,List.of(Map.of("type","SPOT","spot_id",spot.getId().toString()))));
 }
 @Test void availabilityIsDerivedAndDoesNotClaimPhysicalOccupancy(){
  var r=reserve();var available=queries.availability(driver,zone.getId(),r.getStartAt(),r.getEndAt());
  assertFalse(available.stream().filter(a->a.spotId().equals(spot.getId())).findFirst().orElseThrow().bookable());
  assertEquals("UNAVAILABLE",available.get(0).physicalOccupancy());
  assertEquals(OperationalStatus.ACTIVE,commands.spot(spot.getId()).getOperationalStatus());
 }
 @Test void driverProfileTypeFiltersZonesAndIsEnforcedForAvailabilityAndReservation(){
  commands.updateZone(admin,zone.getId(),zone.getName(),zone.getLocation(),zone.getColorCode(),zone.getStatus(),Set.of(DriverProfileType.TAXI));
  assertTrue(queries.zones(driver,tenant.getId()).isEmpty());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->queries.availability(driver,zone.getId(),now.get().plusSeconds(60),now.get().plusSeconds(3600)));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,this::reserve);

  profiles.assign(admin,tenant.getId(),profiles.driver(driver).getId(),DriverProfileType.TAXI);
  assertEquals(Set.of(DriverProfileType.TAXI),queries.zones(driver,tenant.getId()).getFirst().getAllowedProfileTypes());
  assertNotNull(reserve());
 }
 @Test void unconfiguredZoneAllowsAllDriverProfileTypesAndDefaultsToPrivate(){
  assertEquals(EnumSet.allOf(DriverProfileType.class),zone.getAllowedProfileTypes());
  assertNotNull(reserve());
 }
 @Test void parkingZoneHttpPersistsAndReturnsAllowedProfileTypes() throws Exception {
  var requestBody=Map.of("name","Taxi only","location",Map.of("reference","East"),"colorCode","#112233","allowedProfileTypes",List.of("TAXI"));
  var created=request("POST","/api/v1/parking-zones?tenantId="+tenant.getId(),requestBody,adminAuth.accessToken());
  assertEquals(201,created.statusCode());
  var jsonZone=json.readTree(created.body());assertEquals(List.of("TAXI"),json.convertValue(jsonZone.path("allowedProfileTypes"),List.class));
  var id=UUID.fromString(jsonZone.path("zoneId").asText());
  assertEquals(403,request("GET","/api/v1/parking-spots/availability?zoneId="+id+"&startAt="+now.get().plusSeconds(60)+"&endAt="+now.get().plusSeconds(3600),null,driverAuth.accessToken()).statusCode());
 }
 @Test void swaggerAndHttpActuallyEnforceRoles() throws Exception {
  assertEquals(401,request("GET","/api/v1/reservations",null,null).statusCode());
  assertEquals(403,request("POST","/api/v1/parking-zones?tenantId="+tenant.getId(),Map.of("name","X","location",Map.of(),"colorCode","#000000"),driverAuth.accessToken()).statusCode());
  assertEquals(200,request("GET","/api/v1/parking-spots?zoneId="+zone.getId(),null,driverAuth.accessToken()).statusCode());
  var docs=request("GET","/v3/api-docs/03-parking",null,null);assertEquals(200,docs.statusCode());
  var paths=json.readTree(docs.body()).path("paths");
  for(String endpoint:List.of("tenants","parking-zones","parking-spots","floor-plans","digital-parking-maps","reservations","parking-sessions","guest-parking-sessions"))assertTrue(paths.has("/api/v1/"+endpoint),endpoint);
  assertFalse(paths.has("/api/v1/parkings"));assertFalse(paths.has("/api/v1/detectedSpots"));
 }
  @Test void invalidUuidAndIntervalProduce400() throws Exception {
  assertEquals(400,request("GET","/api/v1/parking-spots?zoneId=not-uuid",null,driverAuth.accessToken()).statusCode());
  assertEquals(400,request("POST","/api/v1/reservations",Map.of("spotId",spot.getId(),"vehicleId",vehicle.getId(),"startAt",now.get(),"endAt",now.get()),driverAuth.accessToken()).statusCode());
  }
  @Test void favoriteEndpointsPersistIdempotentlyAndEnforceOwnerScope() throws Exception {
   var body=Map.of("tenantId",tenant.getId());
   var created=request("POST","/api/v1/favorites",body,driverAuth.accessToken());
   assertEquals(201,created.statusCode());
   var favorite=json.readTree(created.body());
   var favoriteId=favorite.path("favoriteId").asText();
   assertFalse(favoriteId.isBlank());
   assertEquals(tenant.getId().toString(),favorite.path("tenantId").asText());

   var repeated=request("POST","/api/v1/favorites",body,driverAuth.accessToken());
   assertEquals(201,repeated.statusCode());
   assertEquals(favoriteId,json.readTree(repeated.body()).path("favoriteId").asText());
   assertEquals(1,json.readTree(request("GET","/api/v1/favorites",null,driverAuth.accessToken()).body()).size());
   assertEquals(0,json.readTree(request("GET","/api/v1/favorites",null,otherAuth.accessToken()).body()).size());

   assertEquals(404,request("DELETE","/api/v1/favorites/"+favoriteId,null,otherAuth.accessToken()).statusCode());
   assertEquals(204,request("DELETE","/api/v1/favorites/"+favoriteId,null,driverAuth.accessToken()).statusCode());
   assertEquals(0,json.readTree(request("GET","/api/v1/favorites",null,driverAuth.accessToken()).body()).size());
  }
 @Test void tariffsValidatePeriodAndRoundOnlyWhenNeeded(){
  var t=now.get();assertEquals(new BigDecimal("5.00"),ParkingTariffs.price(rules(),t,t.plusSeconds(3600)));
  assertEquals(new BigDecimal("10.00"),ParkingTariffs.price(rules(),t,t.plusSeconds(3601)));
  assertThrows(IllegalArgumentException.class,()->ParkingTariffs.validate(Map.of("tariff_rules",Map.of("period_minutes",0,"price_per_period",5))));
 }
}
