package com.yachiqo.spotgo.billing.application;
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
 "spring.datasource.url=jdbc:h2:mem:billing-integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE,HOUR,ROWNUM,DAY",
 "app.parking.lock-cleanup-ms=3600000"
})
@ActiveProfiles("test")
class BillingIntegrationTest {
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
  for(String table:List.of("billing_documents","refunds","outstanding_balances","additional_charges","subscriptions","payment_attempts",
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
 @Test void tokenMetadataComesFromProviderAndNeverStoresPanOrCvv() throws Exception {
  var t=token(driver);assertEquals(driverProfile.getId(),t.getProfileId());assertEquals("4242",t.getLastFour());
  var response=request("GET","/api/v1/payment-tokens",null,driverAuth.accessToken());assertEquals(200,response.statusCode());assertFalse(response.body().contains("providerTokenRef"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE lower(table_name)='payment_tokens' AND lower(column_name) IN ('pan','cvv','card_number')",Integer.class));
 }
 @Test void revokingTokenPreventsNewPayments(){
  var t=token(driver);billing.revokeToken(driver,t.getId());var r=reserve();
  assertThrows(BillingException.class,()->billing.reservationPayment(driver,r.getId(),t.getId(),"revoked"));
  assertThrows(AccessDeniedException.class,()->billing.revokeToken(other,t.getId()));
 }
 @Test void nativeUuidAndIdempotencyConstraintsArePhysical(){
  var r=reserve();var p=billing.reservationPayment(driver,r.getId(),token(driver).getId(),"once");
  assertEquals(p.getId(),jdbc.queryForObject("SELECT payment_id FROM reservations WHERE reservation_id=?",UUID.class,r.getId()));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE digital_payments SET profile_id=? WHERE payment_id=?",UUID.randomUUID(),p.getId()));
 }
 @Test void sameRequestReturnsSamePaymentWithoutChargingAgain(){
  var r=reserve();var t=token(driver);var p=billing.reservationPayment(driver,r.getId(),t.getId(),"same");
  assertEquals(p.getId(),billing.reservationPayment(driver,r.getId(),t.getId(),"same").getId());
  assertEquals(1,attempts.byPayment(p.getId()).size());verify(provider,never()).charge(any(),anyInt(),anyString(),any(),anyString());
  billing.process(driver,p.getId(),false);billing.process(driver,p.getId(),false);
  verify(provider,times(1)).charge(any(),anyInt(),anyString(),any(),anyString());
 }
 @Test void sameKeyDifferentPayloadIsRejected(){
  var r=reserve();var one=token(driver);var two=token(driver);billing.reservationPayment(driver,r.getId(),one.getId(),"same");
  assertThrows(BillingException.class,()->billing.reservationPayment(driver,r.getId(),two.getId(),"same"));
 }
 @Test void concurrentSameRequestCreatesOnlyOneOperation() throws Exception {
  var r=reserve();var t=token(driver);
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var gate=new CountDownLatch(1);
   var a=executor.submit(()->{gate.await();return billing.reservationPayment(driver,r.getId(),t.getId(),"concurrent").getId();});
   var b=executor.submit(()->{gate.await();return billing.reservationPayment(driver,r.getId(),t.getId(),"concurrent").getId();});
   gate.countDown();assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
  }
  assertEquals(1,payments.findAll().size());
 }
 @Test void approvedPaymentConfirmsOnlyItsReservation(){
  var r=reserve();var p=payReservation(r);assertEquals(PaymentStatus.APPROVED,p.getStatus());assertNotNull(p.getApprovedAt());
  assertEquals(ReservationStatus.RESERVED,reservations.findById(r.getId()).orElseThrow().getStatus());assertEquals(PaymentAttemptStatus.SUCCESS,attempts.byPayment(p.getId()).get(0).getStatus());
 }
 @Test void anotherDriverCannotPayOrProcessMyOperation(){
  var r=reserve();var t=token(driver);
  assertThrows(AccessDeniedException.class,()->billing.reservationPayment(other,r.getId(),token(other).getId(),"steal"));
  assertThrows(AccessDeniedException.class,()->billing.reservationPayment(driver,r.getId(),token(other).getId(),"wrong-token"));
  var p=billing.reservationPayment(driver,r.getId(),t.getId(),"own");
  assertThrows(AccessDeniedException.class,()->billing.process(other,p.getId(),false));
 }
 @Test void rejectedPaymentCanRetryOnSameOperationAndAttemptHistory(){
  var r=reserve();var p=billing.reservationPayment(driver,r.getId(),token(driver).getId(),"retry");outcome.set(PaymentStatus.REJECTED);
  billing.process(driver,p.getId(),false);assertEquals(1,attempts.byPayment(p.getId()).size());
  assertThrows(BillingException.class,()->billing.process(driver,p.getId(),true));
  now.set(now.get().plusSeconds(61));outcome.set(PaymentStatus.APPROVED);
  assertEquals(PaymentStatus.APPROVED,billing.process(driver,p.getId(),true).getStatus());assertEquals(2,attempts.byPayment(p.getId()).size());
 }
 @Test void pendingResultMustReconcileRatherThanChargeAgain(){
  var r=reserve();var p=billing.reservationPayment(driver,r.getId(),token(driver).getId(),"pending");outcome.set(PaymentStatus.PENDING);
  billing.process(driver,p.getId(),false);assertThrows(BillingException.class,()->billing.process(driver,p.getId(),true));
  outcome.set(PaymentStatus.APPROVED);billing.reconcile(driver,p.getId());assertEquals(ReservationStatus.RESERVED,reservations.findById(r.getId()).orElseThrow().getStatus());
  verify(provider,times(1)).charge(any(),anyInt(),anyString(),any(),anyString());
 }
 @Test void unknownProviderFailureIsRecordedWithoutRetryingBlindly(){
  var r=reserve();var p=billing.reservationPayment(driver,r.getId(),token(driver).getId(),"unknown");
  when(provider.charge(any(),anyInt(),anyString(),any(),anyString())).thenThrow(new IllegalStateException("connection lost"));
  assertEquals(PaymentStatus.RECONCILIATION_REQUIRED,billing.process(driver,p.getId(),false).getStatus());
  assertEquals(PaymentAttemptStatus.UNKNOWN,attempts.byPayment(p.getId()).get(0).getStatus());
  assertThrows(BillingException.class,()->billing.process(driver,p.getId(),true));
 }
 @Test void lateApprovalCreatesRefundWithoutConfirmingExpiredReservation(){
  var r=reserve();var p=billing.reservationPayment(driver,r.getId(),token(driver).getId(),"late");outcome.set(PaymentStatus.PENDING);billing.process(driver,p.getId(),false);
  now.set(now.get().plusSeconds(601));outcome.set(PaymentStatus.APPROVED);billing.reconcile(driver,p.getId());
  assertEquals(ReservationStatus.CANCELLED,reservations.findById(r.getId()).orElseThrow().getStatus());assertEquals(1,refunds.byPayment(p.getId()).size());
  billing.reconcile(driver,p.getId());assertEquals(1,refunds.byPayment(p.getId()).size());
 }
 @Test void unconfiguredProviderCannotApproveAnything(){
  var r=reserve();var p=billing.reservationPayment(driver,r.getId(),token(driver).getId(),"unavailable");
  when(provider.charge(any(),anyInt(),anyString(),any(),anyString())).thenThrow(new BillingException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"not configured"));
  assertThrows(BillingException.class,()->billing.process(driver,p.getId(),false));assertEquals(PaymentStatus.INITIATED,payments.findById(p.getId()).orElseThrow().getStatus());
 }
 @Test void subscriptionUsesExactlyOneOwnerAndOneApprovedPayment(){
  var p=billing.subscriptionPayment(driver,"DRIVER_TEST",token(driver).getId(),"sub");
  assertEquals(0,subscriptions.count());
  billing.process(driver,p.getId(),false);
  var active=subscriptions.byPayment(p.getId()).orElseThrow();
  assertEquals(driverProfile.getId(),active.getProfileId());assertNull(active.getTenantId());assertNull(active.getEndAt());
  assertEquals(SubscriptionStatus.ACTIVE,active.getStatus());assertEquals(p.getId(),active.getPaymentId());
  assertThrows(BillingException.class,()->billing.subscriptionPayment(driver,"DRIVER_TEST",p.getTokenId(),"another"));
  billing.process(driver,p.getId(),false);assertEquals(1,subscriptions.count());
  assertEquals(p.getId(),billing.subscriptionPayment(driver,"DRIVER_TEST",p.getTokenId(),"sub").getId());
 }
 @Test void driverMembershipDiscountChangesServerReservationAmount(){
  var p=billing.subscriptionPayment(driver,"DRIVER_TEST",token(driver).getId(),"discount");billing.process(driver,p.getId(),false);
  assertEquals(new BigDecimal("8.00"),reserve().getAmount());
 }
 @Test void unpaidSubscriptionDoesNotExistAndDoesNotGiveDiscount(){
  var p=billing.subscriptionPayment(driver,"DRIVER_TEST",token(driver).getId(),"unpaid-sub");
  assertEquals(0,subscriptions.count());assertEquals(new BigDecimal("10.00"),reserve().getAmount());
  outcome.set(PaymentStatus.REJECTED);billing.process(driver,p.getId(),false);assertEquals(0,subscriptions.count());
 }
 @Test void tenantSubscriptionIsOwnedByTenantNotByPayingAdministrator(){
  var p=billing.subscriptionPayment(admin,"TENANT_TEST",token(admin).getId(),"tenant-sub");assertEquals(0,subscriptions.count());
  billing.process(admin,p.getId(),false);var sub=subscriptions.byPayment(p.getId()).orElseThrow();
  assertNull(sub.getProfileId());assertEquals(tenant.getId(),sub.getTenantId());
  assertEquals(adminProfile.getId(),p.getProfileId());assertEquals(SubscriptionStatus.ACTIVE,sub.getStatus());
  assertThrows(AccessDeniedException.class,()->billing.subscriptionPayment(driver,"TENANT_TEST",token(driver).getId(),"wrong-plan"));
 }
 @Test void cancelledSubscriptionKeepsHistoryAndRetakingCreatesAnotherRow(){
  var p=billing.subscriptionPayment(driver,"DRIVER_TEST",token(driver).getId(),"first-period");billing.process(driver,p.getId(),false);
  var sub=subscriptions.byPayment(p.getId()).orElseThrow();billing.cancelSubscription(driver,sub.getId());
  var nextPayment=billing.subscriptionPayment(driver,"DRIVER_TEST",p.getTokenId(),"second-period");billing.process(driver,nextPayment.getId(),false);
  var next=subscriptions.byPayment(nextPayment.getId()).orElseThrow();
  assertNotEquals(sub.getId(),next.getId());assertNotNull(subscriptions.findById(sub.getId()).orElseThrow().getEndAt());assertEquals(2,subscriptions.count());
 }
 @Test void expiredPeriodDoesNotGrantBenefitsAndRenewalKeepsOriginalRow(){
  var p=billing.subscriptionPayment(driver,"DRIVER_TEST",token(driver).getId(),"month");billing.process(driver,p.getId(),false);
  var sub=subscriptions.byPayment(p.getId()).orElseThrow();var start=sub.getStartAt();
  var end=start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();now.set(end.plusSeconds(1));
  assertEquals(new BigDecimal("10.00"),reserve().getAmount());billing.expirePeriods();
  var expired=subscriptions.findById(sub.getId()).orElseThrow();assertEquals(end,expired.getEndAt());assertEquals(start,expired.getStartAt());
  var next=billing.subscriptionPayment(driver,"DRIVER_TEST",p.getTokenId(),"renewed-month");billing.process(driver,next.getId(),false);
  assertEquals(2,subscriptions.count());assertNotEquals(sub.getId(),subscriptions.byPayment(next.getId()).orElseThrow().getId());
 }
 @Test void subscriptionStartsWhenPaymentIsApprovedAndKeepsPlanSnapshot(){
  var p=billing.subscriptionPayment(driver,"DRIVER_TEST",token(driver).getId(),"delayed-sub");
  when(plans.get("DRIVER_TEST")).thenThrow(new IllegalArgumentException("Plan retirado del catalogo"));
  now.set(now.get().plusSeconds(3600));outcome.set(PaymentStatus.RECONCILIATION_REQUIRED);billing.process(driver,p.getId(),false);
  assertEquals(0,subscriptions.count());now.set(now.get().plusSeconds(3600));outcome.set(PaymentStatus.APPROVED);billing.reconcile(driver,p.getId());
  var sub=subscriptions.byPayment(p.getId()).orElseThrow();assertEquals(now.get(),sub.getStartAt());assertEquals("DRIVER_TEST",sub.getPlan().get("code"));
  billing.reconcile(driver,p.getId());assertEquals(1,subscriptions.count());
 }
 @Test void subscriptionCannotBeConstructedWithoutAnApprovedPayment(){
  assertThrows(IllegalArgumentException.class,()->new Subscription(driverProfile,null,Map.of("billing_period_months",1),null,now.get()));
  var p=billing.subscriptionPayment(driver,"DRIVER_TEST",token(driver).getId(),"not-paid");
  assertThrows(IllegalArgumentException.class,()->new Subscription(driverProfile,null,Map.of("billing_period_months",1),p,now.get()));
 }
 @Test void databaseRejectsSubscriptionWithNullPayment(){
  var p=billing.subscriptionPayment(driver,"DRIVER_TEST",token(driver).getId(),"required-fk");billing.process(driver,p.getId(),false);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE subscriptions SET payment_id=NULL WHERE payment_id=?",p.getId()));
 }
 @Test void httpCreatesOnlyPaymentUntilApprovalAndDoesNotExposeUnpaidSubscriptionCreation() throws Exception{
  var t=token(driver);
  var response=request("POST","/api/v1/digital-payments/subscriptions",Map.of("planCode","DRIVER_TEST","tokenId",t.getId(),"idempotencyKey","http-sub"),driverAuth.accessToken());
  assertEquals(201,response.statusCode());assertEquals(0,subscriptions.count());
  var p=payments.findByIdempotencyKey("http-sub").orElseThrow();
  assertEquals(200,request("POST","/api/v1/digital-payments/"+p.getId()+"/process",null,driverAuth.accessToken()).statusCode());
  assertEquals(1,subscriptions.count());
  assertEquals(405,request("POST","/api/v1/subscriptions",Map.of("planCode","DRIVER_TEST"),driverAuth.accessToken()).statusCode());
 }
 @Test void twoPendingPaymentsCannotCreateOverlappingSubscriptions(){
  var t=token(driver);var p=billing.subscriptionPayment(driver,"DRIVER_TEST",t.getId(),"pending-one");
  outcome.set(PaymentStatus.RECONCILIATION_REQUIRED);billing.process(driver,p.getId(),false);
  var other=billing.subscriptionPayment(driver,"DRIVER_TEST",t.getId(),"pending-two");outcome.set(PaymentStatus.APPROVED);billing.process(driver,other.getId(),false);
  billing.reconcile(driver,p.getId());assertEquals(1,subscriptions.count());assertEquals(1,refunds.byPayment(p.getId()).size());
  assertEquals(other.getId(),subscriptions.findAll().get(0).getPaymentId());
 }
 @Test void unpaidChargesBlockOnlyNewReservationsAndRegularizeThroughPayment() throws Exception {
  var c=charge();billing.markUnpaid(admin,c.getId());assertEquals(new BigDecimal("5.00"),queries.outstandingTotal(driver));
  assertThrows(BillingException.class,this::reserve);
  driverAuth=iam.refresh(driverAuth.refreshToken(),"TEST");
  assertEquals(200,request("GET","/api/v1/outstanding-balances",null,driverAuth.accessToken()).statusCode());
  var p=billing.chargePayment(driver,c.getId(),token(driver).getId(),"debt");billing.process(driver,p.getId(),false);
  var b=balances.byCharge(c.getId()).orElseThrow();assertEquals(OutstandingBalanceStatus.REGULARIZED,b.getStatus());assertEquals(0,b.getAmount().signum());assertNotNull(b.getRegularizedAt());
  assertEquals(AdditionalChargeStatus.PAID,charges.findById(c.getId()).orElseThrow().getStatus());assertNotNull(reserve().getId());
 }
 @Test void rejectedChargeCreatesOnlyOneDebtAndRetryRegularizesIt(){
  var c=charge();var p=billing.chargePayment(driver,c.getId(),token(driver).getId(),"charge-retry");outcome.set(PaymentStatus.REJECTED);billing.process(driver,p.getId(),false);
  assertEquals(1,balances.findAll().size());billing.markUnpaid(admin,c.getId());assertEquals(1,balances.findAll().size());
  now.set(now.get().plusSeconds(61));outcome.set(PaymentStatus.APPROVED);billing.process(driver,p.getId(),true);assertEquals(0,queries.outstandingTotal(driver).signum());
 }
 @Test void pendingChargeIsNotDeclaredDefinitivelyUnpaid(){
  var c=charge();var p=billing.chargePayment(driver,c.getId(),token(driver).getId(),"charge-pending");outcome.set(PaymentStatus.PENDING);billing.process(driver,p.getId(),false);
  assertThrows(BillingException.class,()->billing.markUnpaid(admin,c.getId()));assertTrue(balances.findAll().isEmpty());
 }
 @Test void oneReservationCanHaveMultipleChargesAndEachDebtKeepsItsOwnAmount(){
  var one=charge();var two=billing.createCharge(admin,one.getReservationId(),new BigDecimal("3.00"),"OTHER_DAMAGE","other-evidence");
  billing.markUnpaid(admin,one.getId());billing.markUnpaid(admin,two.getId());
  assertEquals(2,queries.charges(driver).size());assertEquals(2,queries.balances(driver).size());assertEquals(new BigDecimal("8.00"),queries.outstandingTotal(driver));
  assertThrows(AccessDeniedException.class,()->billing.chargePayment(other,one.getId(),token(other).getId(),"not-mine"));
 }
 @Test void closingOverstayedSessionCreatesChargeAndDebtExactlyOnce(){
  var r=reserve();payReservation(r);now.set(r.getStartAt());var session=parking.enter(admin,r.getId());now.set(r.getEndAt().plusSeconds(301));parking.exit(admin,session.getId());
  assertEquals(1,charges.byReservation(r.getId()).size());assertEquals(new BigDecimal("5.00"),queries.outstandingTotal(driver));
  assertThrows(IllegalArgumentException.class,()->parking.exit(admin,session.getId()));assertEquals(1,charges.byReservation(r.getId()).size());
 }
 @Test void cancellationRequestsRefundOfOriginalPayment(){
  var r=reserve();var p=payReservation(r);parking.cancel(driver,r.getId());var list=refunds.byPayment(p.getId());assertEquals(1,list.size());assertEquals(p.getAmount(),list.get(0).getAmount());
  billing.processRefund(admin,list.get(0).getId());billing.processRefund(admin,list.get(0).getId());
  verify(provider,times(1)).refund(any(),anyString(),any(),anyString());assertEquals(RefundStatus.COMPLETED,refunds.findById(list.get(0).getId()).orElseThrow().getStatus());
 }
 @Test void refundsCannotExceedOriginalPaymentAndRequestReplayIsIdempotent(){
  var p=payReservation(reserve());var one=billing.requestRefund(admin,p.getId(),new BigDecimal("6.00"),"first");
  assertEquals(one.getId(),billing.requestRefund(admin,p.getId(),new BigDecimal("6.00"),"first").getId());
  assertThrows(BillingException.class,()->billing.requestRefund(admin,p.getId(),new BigDecimal("5.00"),"too much"));
  assertNotNull(billing.requestRefund(admin,p.getId(),new BigDecimal("4.00"),"remaining").getId());
 }
 @Test void concurrentRefundRequestsCannotOverCommitBudget() throws Exception{
  var p=payReservation(reserve());
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var gate=new CountDownLatch(1);
   var one=executor.submit(()->{gate.await();try{billing.requestRefund(admin,p.getId(),new BigDecimal("6.00"),"one");return true;}catch(BillingException e){return false;}});
   var two=executor.submit(()->{gate.await();try{billing.requestRefund(admin,p.getId(),new BigDecimal("6.00"),"two");return true;}catch(BillingException e){return false;}});
   gate.countDown();assertNotEquals(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
  }
 }
 @Test void documentsRequireApprovedPaymentMatchingTaxDataAndUniquePayment(){
  var r=reserve();var p=billing.reservationPayment(driver,r.getId(),token(driver).getId(),"doc");
  assertThrows(IllegalArgumentException.class,()->billing.issueDocument(admin,p.getId(),BillingDocumentType.ELECTRONIC_RECEIPT,"00001","B001","Juan Demo",Map.of("document_type","DNI","document_number","00000000")));
  billing.process(driver,p.getId(),false);var doc=billing.issueDocument(admin,p.getId(),BillingDocumentType.ELECTRONIC_RECEIPT,"00001","B001","Juan Demo",Map.of("document_type","DNI","document_number","00000000"));
  assertEquals(doc.getId(),billing.issueDocument(admin,p.getId(),BillingDocumentType.ELECTRONIC_RECEIPT,"00001","B001","Juan Demo",Map.of("document_type","DNI","document_number","00000000")).getId());
  assertThrows(BillingException.class,()->billing.issueDocument(admin,p.getId(),BillingDocumentType.ELECTRONIC_INVOICE,"00002","F001","Otra",Map.of("document_type","RUC","document_number","00000000000")));
  assertEquals(BillingDocumentStatus.VOID,billing.voidDocument(admin,doc.getId()).getStatus());
 }
 @Test void swaggerShowsCurrentBillingAndNoOldControllers() throws Exception{
  var response=request("GET","/v3/api-docs/04-backend-completo",null,null);assertEquals(200,response.statusCode());var paths=new ObjectMapper().readTree(response.body()).path("paths");
  for(String path:List.of("payment-tokens","digital-payments","payment-attempts","subscriptions","additional-charges","outstanding-balances","refunds","billing-documents"))assertTrue(paths.has("/api/v1/"+path),path);
  for(String old:List.of("receipts","clientPlans","parkings","detectedSpots","employees","occupancyByHour","weeklyTrends"))assertFalse(paths.has("/api/v1/"+old),old);
 }
 @Test void httpDoesNotAllowDriverToCreateChargesOrIssueDocuments() throws Exception{
  assertEquals(403,request("POST","/api/v1/additional-charges",Map.of("reservationId",UUID.randomUUID(),"amount",5,"reason","fake","evidenceRef","fake"),driverAuth.accessToken()).statusCode());
  assertEquals(401,request("GET","/api/v1/digital-payments",null,null).statusCode());
 }
 @Test void seederLoadsAllCurrentTablesWithRealFkRelationships() throws Exception{
  new com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.seeder.DbSeeder(jdbc,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()).run();
  assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM digital_payments WHERE idempotency_key like 'demo-%' OR payment_id IN (?, ?, ?)",Integer.class,UUID.fromString("00000000-0000-4000-8000-000000000081"),UUID.fromString("00000000-0000-4000-8000-000000000082"),UUID.fromString("00000000-0000-4000-8000-000000000083")));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions",Integer.class));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM outstanding_balances",Integer.class));
  var fixture=subscriptions.findById(UUID.fromString("00000000-0000-4000-8000-000000000191")).orElseThrow();assertNotNull(fixture.getPaymentId());assertNotNull(fixture.getPlan().get("price"));
 }
 @Test void allImplementedFixtureTablesHaveExactlyTheDocumentedColumns() throws Exception {
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  com.fasterxml.jackson.databind.JsonNode fixtures;
  try(var input=new org.springframework.core.io.ClassPathResource("db.json").getInputStream()){fixtures=mapper.readTree(input);}
  var missing=new java.util.HashSet<String>();int implemented=0;
  var tables=fixtures.fields();
  while(tables.hasNext()){
   var entry=tables.next();String table=entry.getKey();
   var exists=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_schema)='public' AND LOWER(table_name)=?",Integer.class,table);
   if(exists==0){missing.add(table);continue;}implemented++;
   var expected=new java.util.HashSet<String>();entry.getValue().get(0).fieldNames().forEachRemaining(expected::add);
   var actual=jdbc.query("SELECT * FROM "+table+" WHERE 1=0",(org.springframework.jdbc.core.ResultSetExtractor<java.util.Set<String>>) rs->{
    var result=new java.util.HashSet<String>();var metadata=rs.getMetaData();
    for(int column=1;column<=metadata.getColumnCount();column++){
     var name=metadata.getColumnName(column).toLowerCase(java.util.Locale.ROOT);result.add(name);
     if(name.endsWith("_id")||name.equals("assigned_by")){
      if(!name.equals("correlation_id"))assertEquals("UUID",metadata.getColumnTypeName(column).toUpperCase(java.util.Locale.ROOT),table+"."+name);
     }
    }return result;
   });
   assertEquals(expected,actual,table+": columnas reales frente a db.json");
  }
  assertEquals(24,implemented);
  assertEquals(java.util.Set.of("occupancy_sensors","occupancy_readings","sensor_health_events","occupancy_conflicts","occupancy_alerts","capacity_snapshots","occupancy_reports"),missing);
 }
}
