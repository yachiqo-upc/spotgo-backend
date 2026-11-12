package com.yachiqo.spotgo.billing.application;
import com.yachiqo.spotgo.billing.domain.model.aggregates.*;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.*;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationChangeStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.OperationalStatus;
import com.yachiqo.spotgo.parking.application.ParkingTariffs;
import com.yachiqo.spotgo.parking.application.ParkingPaymentGateway;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.shared.application.events.SpotgoDomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.math.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static com.yachiqo.spotgo.billing.domain.model.valueobjects.BillingValues.*;

@Service @Transactional(readOnly=true)
public class BillingService {
 private final PaymentTokenRepository tokens;
 private final DigitalPaymentRepository payments;
 private final PaymentAttemptRepository attempts;
 private final SubscriptionRepository subscriptions;
 private final AdditionalChargeRepository charges;
 private final OutstandingBalanceRepository balances;
 private final RefundRepository refunds;
 private final BillingDocumentRepository documents;
  private final ReservationRepository reservations;
  private final ReservationChangeRepository reservationChanges;
 private final TenantRepository tenants;
 private final ParkingSpotRepository parkingSpots;
 private final BillingAccess access;
 private final SubscriptionPlans plans;
 private final PaymentProvider provider;
  private final SpotgoDomainEventPublisher domainEvents;
 private final Clock clock;
 private final int retrySeconds;
 private final int debtHours;
 private final boolean afterTolerance;
  public BillingService(PaymentTokenRepository tokens,DigitalPaymentRepository payments,PaymentAttemptRepository attempts,
   SubscriptionRepository subscriptions,AdditionalChargeRepository charges,OutstandingBalanceRepository balances,
   RefundRepository refunds,BillingDocumentRepository documents,ReservationRepository reservations,ReservationChangeRepository reservationChanges,TenantRepository tenants,ParkingSpotRepository parkingSpots,
   BillingAccess access,SubscriptionPlans plans,PaymentProvider provider,SpotgoDomainEventPublisher domainEvents,Clock clock,
  @Value("${app.billing.retry-delay-seconds:60}") int retrySeconds,
  @Value("${app.billing.debt-due-hours:24}") int debtHours,
  @Value("${app.billing.overstay-charge-basis:AFTER_TOLERANCE}") String basis) {
  this.tokens=tokens;this.payments=payments;this.attempts=attempts;this.subscriptions=subscriptions;this.charges=charges;
   this.balances=balances;this.refunds=refunds;this.documents=documents;this.reservations=reservations;this.reservationChanges=reservationChanges;this.tenants=tenants;this.parkingSpots=parkingSpots;
   this.access=access;this.plans=plans;this.provider=provider;this.domainEvents=domainEvents;this.clock=clock;this.retrySeconds=retrySeconds;this.debtHours=debtHours;
  if(retrySeconds<1||debtHours<1||!Set.of("AFTER_TOLERANCE","TOTAL_EXCESS").contains(basis))throw new IllegalArgumentException("Invalid billing policy.");
  afterTolerance=basis.equals("AFTER_TOLERANCE");
 }
 public static BillingException missing(String resource){return new BillingException(HttpStatus.NOT_FOUND,resource+" not found");}
 public static BillingException conflict(String reason){return new BillingException(HttpStatus.CONFLICT,reason);}
 @Transactional
  public PaymentToken registerToken(SpotgoUserPrincipal actor,String reference) {
   var profile=access.profile(actor);access.lockProfile(profile.getId());
   var details=provider.inspectToken(text(reference));
   var token=new PaymentToken(profile,details.reference(),details.brand(),details.lastFour(),details.expirationMonth(),details.expirationYear(),clock.instant());
   if(!token.usable(clock.instant()))throw new IllegalArgumentException("Payment method has expired.");
   if(actor.getRole()==UserRole.DRIVER&&tokens.byProfile(profile.getId()).stream().noneMatch(PaymentToken::isDefault))token.setDefault(true);
   return tokens.saveAndFlush(token);
  }
 @Transactional
 public PaymentToken revokeToken(SpotgoUserPrincipal actor,UUID id){
  var profile=access.profile(actor);access.lockProfile(profile.getId());
  if(actor.getRole()==UserRole.PARKING_ADMIN){access.tenant(actor,actor.getTenantId());tenants.lock(actor.getTenantId()).orElseThrow(()->missing("Tenant"));}
  var t=tokens.lock(id).orElseThrow(()->missing("Payment token"));access.owner(actor,t.getProfile());
  boolean wasDriverDefault=actor.getRole()==UserRole.DRIVER&&t.isDefault();
  for(var tenant:tenants.byDefaultPaymentTokenId(t.getId())){var locked=tenants.lock(tenant.getId()).orElseThrow(()->missing("Tenant"));locked.setDefaultPaymentTokenId(null);}
  t.revoke(clock.instant());t.setDefault(false);
  if(wasDriverDefault)tokens.byProfile(t.getProfileId()).stream().filter(candidate->candidate.usable(clock.instant())).findFirst().ifPresent(candidate->candidate.setDefault(true));
  return tokens.saveAndFlush(t);
 }
 @Transactional
 public PaymentToken setDefaultPaymentToken(SpotgoUserPrincipal actor,UUID id){
   if(actor==null)throw new AccessDeniedException("Authentication is required.");
   if(actor.getRole()==UserRole.DRIVER){
    var profile=access.profile(actor);access.lockProfile(profile.getId());
    var token=tokens.lock(id).orElseThrow(()->missing("Payment token"));access.owner(actor,token.getProfile());
    if(!token.usable(clock.instant()))throw conflict("The payment method is revoked or expired.");
    tokens.byProfile(profile.getId()).forEach(candidate->candidate.setDefault(candidate.getId().equals(token.getId())));
    return token;
   }
   if(actor.getRole()!=UserRole.PARKING_ADMIN)throw new AccessDeniedException("Only a driver or PARKING_ADMIN can configure a default payment method.");
  var profile=access.profile(actor);access.lockProfile(profile.getId());access.tenant(actor,actor.getTenantId());
  var tenant=tenants.lock(actor.getTenantId()).orElseThrow(()->missing("Tenant"));
  var token=tokens.lock(id).orElseThrow(()->missing("Payment token"));access.owner(actor,token.getProfile());
  if(!token.usable(clock.instant()))throw conflict("The payment method is revoked or expired.");
  tenant.setDefaultPaymentTokenId(token.getId());tenants.saveAndFlush(tenant);return token;
 }
 public UUID defaultPaymentTokenId(SpotgoUserPrincipal actor){
  if(actor==null||actor.getRole()!=UserRole.PARKING_ADMIN)return null;
  access.tenant(actor,actor.getTenantId());return tenants.findById(actor.getTenantId()).map(Tenant::getDefaultPaymentTokenId).orElse(null);
 }
 public void lockProfile(UUID profile){access.lockProfile(profile);}
 public void requireNoDebt(UUID profile){
  if(balances.byProfile(profile).stream().anyMatch(b->b.getStatus()==OutstandingBalanceStatus.OPEN&&b.getAmount().signum()>0))
   throw conflict("Settle your outstanding balances before creating another reservation. You can view and pay your balances.");
 }
 public int sensorLimit(UUID tenant){
  var now=clock.instant();var active=subscriptions.byTenant(tenant).stream().filter(s->s.activeAt(now)&&now.isBefore(SubscriptionPlans.periodEnd(s))).toList();
  if(active.size()!=1)return 0;
  try{return Math.max(0,new BigDecimal(active.get(0).getPlan().get("included_sensors").toString()).intValueExact());}
  catch(RuntimeException e){return 0;}
 }
 public boolean spotLicensed(UUID tenantId,UUID spotId){
  var active=activeTenantSubscription(tenantId,clock.instant());
  if(active==null)return false;
  var plan=active.getPlan();
  if(!plans.isSpotPricedTenantPlan(plan))return true;
  return plans.licensedSpotIds(plan).contains(spotId.toString());
 }
 public boolean canActivateSpot(UUID tenantId,UUID spotId){
  var active=activeTenantSubscription(tenantId,clock.instant());
  return active==null||!plans.isSpotPricedTenantPlan(active.getPlan())||
   plans.licensedSpotIds(active.getPlan()).contains(spotId.toString());
 }
 private Subscription activeTenantSubscription(UUID tenantId,Instant now){
  var active=subscriptions.byTenant(tenantId).stream()
   .filter(s->s.activeAt(now)&&now.isBefore(SubscriptionPlans.periodEnd(s))).toList();
  if(active.size()>1)throw conflict("The tenant has more than one active subscription.");
  return active.isEmpty()?null:active.get(0);
 }
 @Transactional
 public void prepareSpotAddition(SpotgoUserPrincipal actor,UUID tenantId){
  access.tenant(actor,tenantId);var profile=access.profile(actor);access.lockProfile(profile.getId());
  tenants.lock(tenantId).orElseThrow(()->missing("Tenant"));
  var active=activeTenantSubscription(tenantId,clock.instant());
  if(active==null){
   if(!payments.tenantSubscriptionRequests(PaymentOperationType.SUBSCRIPTION,
    Set.of(PaymentStatus.INITIATED,PaymentStatus.PENDING,PaymentStatus.RECONCILIATION_REQUIRED),"%"+tenantId+"%").isEmpty())
    throw conflict("Wait for the subscription payment to finish before adding parking spots.");
   return;
  }
  if(!plans.isSpotPricedTenantPlan(active.getPlan()))return;
  var existingIds=parkingSpots.byTenant(tenantId).stream().map(ParkingSpot::getId).map(UUID::toString).collect(java.util.stream.Collectors.toSet());
  var licensedIds=plans.licensedSpotIds(active.getPlan());
  if(!licensedIds.containsAll(existingIds))throw conflict("Resolve the pending charge for the previous parking spot before adding another.");
 }
 @Transactional
 public ParkingPaymentGateway.SpotBillingResult billSpotAddition(SpotgoUserPrincipal actor,ParkingSpot spot){
  UUID tenantId=spot.getTenantId();access.tenant(actor,tenantId);
  var profile=access.profile(actor);access.lockProfile(profile.getId());
  var tenant=tenants.lock(tenantId).orElseThrow(()->missing("Tenant"));
  var subscription=activeTenantSubscription(tenantId,clock.instant());
  if(subscription==null||!plans.isSpotPricedTenantPlan(subscription.getPlan()))return null;

  var currentSpotIds=parkingSpots.byTenant(tenantId).stream().map(ParkingSpot::getId).toList();
  var licensed=plans.licensedSpotIds(subscription.getPlan());
  var added=currentSpotIds.stream().map(UUID::toString).filter(id->!licensed.contains(id)).toList();
  if(added.isEmpty())return null;
  if(added.size()!=1||!added.contains(spot.getId().toString()))
   throw conflict("Resolve the pending charge for the previous parking spot before adding another.");

  UUID defaultTokenId=tenant.getDefaultPaymentTokenId();
  if(defaultTokenId==null)throw conflict("Set a default payment method for the tenant before adding parking spots.");
  var token=tokens.lock(defaultTokenId).orElseThrow(()->conflict("The tenant's default payment method is no longer available."));
  access.owner(actor,token.getProfile());
  if(!token.usable(clock.instant()))throw conflict("The tenant's default payment method is revoked or expired.");

  var newPlan=plans.snapshotForTenant(subscription.getPlan(),currentSpotIds);
  BigDecimal prorated=proratedDevicePrice(subscription,added.size(),clock.instant());
  if(prorated.signum()<=0){
   subscription.updatePlan(newPlan);subscriptions.saveAndFlush(subscription);
   spot.setOperationalStatus(OperationalStatus.ACTIVE);parkingSpots.saveAndFlush(spot);
   return null;
  }

  spot.setOperationalStatus(OperationalStatus.INACTIVE);parkingSpots.saveAndFlush(spot);
  String key="spot-proration-"+spot.getId();
  String request=encodeSubscriptionAdjustmentRequest(subscription.getId(),spot.getId(),newPlan);
  String requestHash=hash(PaymentOperationType.SUBSCRIPTION_ADJUSTMENT,spot.getId(),token.getId(),profile,prorated);
  var payment=create(profile,token,PaymentOperationType.SUBSCRIPTION_ADJUSTMENT,prorated,key,requestHash);
  payment.subscriptionAdjustmentRequest(request);payments.saveAndFlush(payment);
  var processed=process(actor,payment.getId(),false);
  return new ParkingPaymentGateway.SpotBillingResult(processed.getId(),processed.getAmount(),processed.getCurrency(),processed.getStatus().name());
 }
 private BigDecimal proratedDevicePrice(Subscription subscription,long newSpotCount,Instant now){
  if(newSpotCount<1)return BigDecimal.ZERO.setScale(2);
  var end=SubscriptionPlans.periodEnd(subscription);
  long totalMillis=Duration.between(subscription.getStartAt(),end).toMillis();
  long remainingMillis=Math.max(0,Duration.between(now,end).toMillis());
  if(totalMillis<=0||remainingMillis==0)return BigDecimal.ZERO.setScale(2);
  BigDecimal fraction=BigDecimal.valueOf(remainingMillis).divide(BigDecimal.valueOf(totalMillis),12,RoundingMode.HALF_UP);
  BigDecimal prorated=plans.pricePerIotDevice(subscription.getPlan()).multiply(BigDecimal.valueOf(newSpotCount)).multiply(fraction);
  return amount(prorated.setScale(2,RoundingMode.HALF_UP));
 }
  public BigDecimal reservationPrice(UUID profile,BigDecimal base,Instant now) {
  var active=subscriptions.byProfile(profile).stream().filter(s->s.activeAt(now)&&now.isBefore(SubscriptionPlans.periodEnd(s))).toList();
  if(active.size()>1)throw conflict("More than one subscription is active. Check the account's subscription configuration.");
  BigDecimal discount=active.isEmpty()?BigDecimal.ZERO:new BigDecimal(active.get(0).getPlan().getOrDefault("discount_percent",0).toString());
  if(discount.signum()<0||discount.compareTo(BigDecimal.valueOf(100))>=0)throw conflict("Invalid subscription discount.");
   return base.multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2))).setScale(2,RoundingMode.HALF_UP);
  }
  @Transactional
  public ParkingPaymentGateway.ReservationChangeResult beginReschedule(SpotgoUserPrincipal actor,Reservation reservation,
    Instant start,Instant end,BigDecimal nextAmount) {
   if(actor==null||actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("Only the driver can reschedule their reservation.");
   var profile=access.profile(actor);access.owner(actor,reservation.getProfile());access.lockProfile(profile.getId());
   if(reservation.getStatus()!=ReservationStatus.RESERVED||reservation.getParentReservationId()!=null)
    throw conflict("Only a confirmed primary reservation can be rescheduled.");
   var previous=reservationChanges.byReservation(reservation.getId());
   if(previous.stream().anyMatch(c->c.getStatus()==ReservationChangeStatus.PENDING_PAYMENT))
    throw conflict("Finish or reconcile the existing reservation change before editing it again.");
   for(var change:previous)if(change.getStatus()==ReservationChangeStatus.REFUND_SCHEDULED){change.supersede();reservationChanges.saveAndFlush(change);}

   var target=amount(nextAmount);
   var available=reservationPaidBalance(reservation,previous);
   var difference=target.subtract(available).setScale(2,RoundingMode.HALF_UP);
   if(difference.signum()>0){
    var change=reservationChanges.saveAndFlush(new ReservationChange(reservation,start,end,target,difference,
      ReservationChangeStatus.PENDING_PAYMENT,null));
    return new ParkingPaymentGateway.ReservationChangeResult(change,null,difference);
   }
   reservation.reschedule(start,end,target);reservations.saveAndFlush(reservation);
   var refundDue=difference.signum()<0?clock.instant().plusSeconds(300):null;
   var change=reservationChanges.saveAndFlush(new ReservationChange(reservation,start,end,target,BigDecimal.ZERO.setScale(2),
     refundDue==null?ReservationChangeStatus.APPLIED:ReservationChangeStatus.REFUND_SCHEDULED,refundDue));
   return new ParkingPaymentGateway.ReservationChangeResult(change,null,BigDecimal.ZERO.setScale(2));
  }
  private BigDecimal reservationPaidBalance(Reservation reservation,List<ReservationChange> changes) {
   var captured=new LinkedHashMap<UUID,DigitalPayment>();
   if(reservation.getPaymentId()!=null)payments.findById(reservation.getPaymentId()).filter(p->p.getStatus()==PaymentStatus.APPROVED)
     .ifPresent(p->captured.put(p.getId(),p));
   for(var change:changes)if(change.getPaymentId()!=null)payments.findById(change.getPaymentId())
     .filter(p->p.getStatus()==PaymentStatus.APPROVED).ifPresent(p->captured.put(p.getId(),p));
   BigDecimal total=BigDecimal.ZERO;
   for(var payment:captured.values()){
    BigDecimal refunded=refunds.byPayment(payment.getId()).stream().filter(r->r.getStatus()!=RefundStatus.FAILED)
      .map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
    total=total.add(payment.getAmount().subtract(refunded));
   }
   return total.max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
  }
 private PaymentToken ownToken(SpotgoUserPrincipal actor,UUID id){
  if(id==null)throw new IllegalArgumentException("Select a payment token.");
  var token=tokens.findById(id).orElseThrow(()->missing("Payment token"));access.owner(actor,token.getProfile());
  if(!token.usable(clock.instant()))throw conflict("Payment token is revoked or expired.");return token;
 }
 private DigitalPayment existing(String key,String hash,Profile profile) {
  var payment=payments.findByIdempotencyKey(text(key)).orElse(null);
  if(payment==null)return null;
  if(!payment.getProfileId().equals(profile.getId()))throw new AccessDeniedException("The idempotency key belongs to another account.");
  if(!payment.getRequestHash().equals(hash))throw conflict("This idempotency key has already been used with a different request.");
  payment.seen(clock.instant());return payments.saveAndFlush(payment);
 }
 private String hash(PaymentOperationType type,UUID target,UUID token,Profile profile,BigDecimal amount){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
   (type+"|"+target+"|"+token+"|"+profile.getId()+"|"+amount.setScale(2)+"|PEN").getBytes(StandardCharsets.UTF_8)));
  }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private DigitalPayment create(Profile profile,PaymentToken token,PaymentOperationType type,BigDecimal amount,String key,String hash) {
  var payment=payments.saveAndFlush(new DigitalPayment(profile,token,type,amount,key,hash,clock.instant()));
  attempts.saveAndFlush(new PaymentAttempt(payment,1,clock.instant()));return payment;
 }
 @Transactional
  public DigitalPayment reservationPayment(SpotgoUserPrincipal actor,UUID reservationId,UUID tokenId,String key) {
  var profile=access.profile(actor);if(actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("Only the driver can pay for their reservation.");
  access.lockProfile(profile.getId());
  var r=reservations.lock(reservationId).orElseThrow(()->missing("Reservation"));access.owner(actor,r.getProfile());
  String hash=hash(PaymentOperationType.RESERVATION,reservationId,tokenId,profile,r.getAmount());
  var replay=existing(key,hash,profile);if(replay!=null)return replay;
  if(r.getPaymentId()!=null)throw conflict("The reservation already has a payment operation. View or retry that operation.");
  if(r.getStatus()!=ReservationStatus.PENDING_PAYMENT||!r.blocks(clock.instant()))throw conflict("The reservation hold is no longer active.");
  var payment=create(profile,ownToken(actor,tokenId),PaymentOperationType.RESERVATION,r.getAmount(),key,hash);
   r.attachPayment(payment.getId());reservations.saveAndFlush(r);return payment;
  }
  @Transactional
  public DigitalPayment reservationChangePayment(SpotgoUserPrincipal actor,UUID changeId,UUID tokenId,String key) {
   var profile=access.profile(actor);if(actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("Only the driver can pay for their reservation change.");
   access.lockProfile(profile.getId());
   var change=reservationChanges.lock(changeId).orElseThrow(()->missing("Reservation change"));
   access.owner(actor,change.getReservation().getProfile());
   if(change.getStatus()!=ReservationChangeStatus.PENDING_PAYMENT)throw conflict("The reservation change is not awaiting payment.");
   String hash=hash(PaymentOperationType.RESERVATION_ADJUSTMENT,changeId,tokenId,profile,change.getPaymentAmount());
   var replay=existing(key,hash,profile);if(replay!=null)return replay;
   if(change.getPaymentId()!=null)throw conflict("The reservation change already has a payment operation. View or retry it.");
   var payment=create(profile,ownToken(actor,tokenId),PaymentOperationType.RESERVATION_ADJUSTMENT,change.getPaymentAmount(),key,hash);
   payment.reservationChangeRequest(changeId);payments.saveAndFlush(payment);
   change.attachPayment(payment.getId());reservationChanges.saveAndFlush(change);return payment;
  }
 @Transactional
 public DigitalPayment subscriptionPayment(SpotgoUserPrincipal actor,String code,UUID tokenId,String key) {
  var profile=access.profile(actor);access.lockProfile(profile.getId());
  // A replay uses its original snapshot even if the server catalog changes later.
  var replay=payments.findByIdempotencyKey(text(key)).orElse(null);
  if(replay!=null){
   access.owner(actor,replay.getProfile());
   var request=subscriptionRequest(replay);
   if(!Objects.equals(request.plan().get("code"),code)||!Objects.equals(replay.getTokenId(),tokenId)||!Objects.equals(request.tenantId(),actor.getRole()==UserRole.PARKING_ADMIN?actor.getTenantId():null))throw conflict("This key is already associated with another request.");
   replay.seen(clock.instant());return payments.saveAndFlush(replay);
  }
  var configured=plans.get(code);boolean driver=actor.getRole()==UserRole.DRIVER;
  if(!configured.get("audience").equals(driver?"DRIVER":"PARKING_ADMIN"))throw new AccessDeniedException("This plan is not available for your role.");
  UUID tenantId=driver?null:actor.getTenantId();if(!driver)access.tenant(actor,tenantId);
  requireNoOpenSubscription(profile.getId(),tenantId);
  var plan=tenantId==null?configured:plans.snapshotForTenant(configured,parkingSpots.byTenant(tenantId).stream().map(ParkingSpot::getId).toList());
  String reference=encodeSubscriptionRequest(plan,tenantId);
  BigDecimal amount=positive(new BigDecimal(plan.get("price").toString()));
  var p=create(profile,ownToken(actor,tokenId),PaymentOperationType.SUBSCRIPTION,amount,key,hash(PaymentOperationType.SUBSCRIPTION,tenantId,tokenId,profile,amount));
  p.subscriptionRequest(reference);return payments.saveAndFlush(p);
 }
 public Map<String,Object> subscriptionQuote(SpotgoUserPrincipal actor,String code){
  var configured=plans.get(code);boolean driver=actor.getRole()==UserRole.DRIVER;
  if(!configured.get("audience").equals(driver?"DRIVER":"PARKING_ADMIN"))throw new AccessDeniedException("This plan is not available for your role.");
  if(driver)return configured;
  UUID tenantId=actor.getTenantId();access.tenant(actor,tenantId);
  return plans.snapshotForTenant(configured,parkingSpots.byTenant(tenantId).stream().map(ParkingSpot::getId).toList());
 }
 private record SubscriptionRequest(Map<String,Object> plan,UUID tenantId) {}
 private record SubscriptionAdjustmentRequest(UUID subscriptionId,List<UUID> spotIds,Map<String,Object> plan) {}
 private String encodeSubscriptionRequest(Map<String,Object> plan,UUID tenant){
  try{return "subscription-request:"+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("plan",plan,"tenant",tenant==null?"":tenant.toString()));}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid plan.",e);}
 }
 private String encodeSubscriptionAdjustmentRequest(UUID subscriptionId,UUID spotId,Map<String,Object> plan){
  try{return "subscription-adjustment:"+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
   Map.of("subscriptionId",subscriptionId.toString(),"spotIds",List.of(spotId.toString()),"plan",plan));}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid subscription adjustment.",e);}
 }
 private SubscriptionRequest subscriptionRequest(DigitalPayment p){
  var fulfilled=subscriptions.byPayment(p.getId());if(fulfilled.isPresent())return new SubscriptionRequest(fulfilled.get().getPlan(),fulfilled.get().getTenantId());
  if(p.getOperationType()!=PaymentOperationType.SUBSCRIPTION||!p.getResultReference().startsWith("subscription-request:"))throw conflict("Payment has no subscription request.");
  try{
   var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var node=mapper.readTree(p.getResultReference().substring("subscription-request:".length()));
   Map<String,Object> plan=mapper.convertValue(node.get("plan"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
   String tenant=node.path("tenant").asText();return new SubscriptionRequest(plan,tenant.isEmpty()?null:UUID.fromString(tenant));
  }catch(Exception e){throw conflict("Invalid subscription request.");}
 }
 private SubscriptionAdjustmentRequest subscriptionAdjustmentRequest(DigitalPayment p){
  if(p.getOperationType()!=PaymentOperationType.SUBSCRIPTION_ADJUSTMENT||!p.getResultReference().startsWith("subscription-adjustment:"))throw conflict("Payment has no subscription adjustment request.");
  try{
   var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
   var node=mapper.readTree(p.getResultReference().substring("subscription-adjustment:".length()));
   var spots=new ArrayList<UUID>();for(var id:node.withArray("spotIds"))spots.add(UUID.fromString(id.asText()));
   Map<String,Object> plan=mapper.convertValue(node.get("plan"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
   return new SubscriptionAdjustmentRequest(UUID.fromString(node.path("subscriptionId").asText()),List.copyOf(spots),plan);
  }catch(Exception e){throw conflict("Invalid subscription adjustment request.");}
 }
 private void requireNoOpenSubscription(UUID profile,UUID tenant){
  if(tenant!=null)tenants.lock(tenant).orElseThrow(()->missing("Tenant"));
  var old=tenant==null?subscriptions.byProfile(profile):subscriptions.byTenant(tenant);
  for(var previous:old)if(previous.getEndAt()==null){
   var end=SubscriptionPlans.periodEnd(previous);
   if(clock.instant().isBefore(end))throw conflict("The account already has an active subscription period.");
   previous.expire(end);
  }
 }
 @Transactional
 public Subscription cancelSubscription(SpotgoUserPrincipal actor,UUID id) {
  var current=subscriptions.findById(id).orElseThrow(()->missing("Subscription"));access.subscription(actor,current);
  access.lockProfile(access.profile(actor).getId());
  var s=subscriptions.lock(id).orElseThrow(()->missing("Subscription"));access.subscription(actor,s);
  if(s.getEndAt()==null)s.close(clock.instant());return subscriptions.saveAndFlush(s);
 }
 @Transactional
 public AdditionalCharge createCharge(SpotgoUserPrincipal actor,UUID reservationId,BigDecimal amount,String reason,String evidence) {
  var current=reservations.findById(reservationId).orElseThrow(()->missing("Reservation"));access.tenant(actor,current.getTenantId());access.lockProfile(current.getProfileId());
  var r=reservations.lock(reservationId).orElseThrow(()->missing("Reservation"));
  if(!Set.of(ReservationStatus.ACTIVE,ReservationStatus.COMPLETED,ReservationStatus.OVERSTAYED).contains(r.getStatus()))
   throw conflict("The charge requires a completed reservation.");
  var duplicate=charges.byReservation(reservationId).stream().filter(c->c.getReason().equals(text(reason))&&c.getEvidenceRef().equals(text(evidence))).findFirst();
  if(duplicate.isPresent()){
   if(duplicate.get().getAmount().compareTo(amount)!=0)throw conflict("The same evidence is already associated with a charge for a different amount.");
   return duplicate.get();
  }
  return charges.saveAndFlush(new AdditionalCharge(r,amount,reason,evidence,clock.instant()));
 }
 private OutstandingBalance unpaid(AdditionalCharge c) {
  if(c.getStatus()==AdditionalChargeStatus.PAID)throw conflict("The charge has already been paid.");
  if(c.getPayment()!=null && c.getPayment().getStatus()!=PaymentStatus.REJECTED)
   throw conflict("Wait for a final payment result before recording the balance as unpaid.");
  return balances.byCharge(c.getId()).orElseGet(()->balances.saveAndFlush(new OutstandingBalance(c,clock.instant().plusSeconds(debtHours*3600L),clock.instant())));
 }
 @Transactional
 public OutstandingBalance markUnpaid(SpotgoUserPrincipal actor,UUID chargeId) {
  var current=charges.findById(chargeId).orElseThrow(()->missing("Charge"));access.tenant(actor,current.getReservation().getTenantId());access.lockProfile(current.getReservation().getProfileId());
  return unpaid(charges.lock(chargeId).orElseThrow(()->missing("Charge")));
 }
 @Transactional
 public DigitalPayment chargePayment(SpotgoUserPrincipal actor,UUID chargeId,UUID tokenId,String key) {
  var profile=access.profile(actor);if(actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("Only the driver can pay their charges.");
  access.lockProfile(profile.getId());
  var c=charges.lock(chargeId).orElseThrow(()->missing("Charge"));access.owner(actor,c.getReservation().getProfile());
  String hash=hash(PaymentOperationType.ADDITIONAL_CHARGE,chargeId,tokenId,profile,c.getAmount());
  var replay=existing(key,hash,profile);if(replay!=null)return replay;
  if(c.getStatus()!=AdditionalChargeStatus.PENDING||c.getPayment()!=null)throw conflict("The charge already has a payment operation; retry that operation.");
  var p=create(profile,ownToken(actor,tokenId),PaymentOperationType.ADDITIONAL_CHARGE,c.getAmount(),key,hash);
  c.attach(p);charges.saveAndFlush(c);return p;
 }
 private DigitalPayment ownPaymentForProcessing(SpotgoUserPrincipal actor,UUID id){
  var current=payments.findById(id).orElseThrow(()->missing("Payment"));access.owner(actor,current.getProfile());
  access.lockProfile(current.getProfileId());
  return payments.lock(id).orElseThrow(()->missing("Payment"));
 }
  private void requireOpenSource(DigitalPayment payment){
   switch(payment.getOperationType()){
    case RESERVATION -> {
     var r=reservations.byPayment(payment.getId()).orElseThrow(()->conflict("Payment has no associated reservation."));
     if(r.getStatus()!=ReservationStatus.PENDING_PAYMENT||!r.blocks(clock.instant()))throw conflict("This reservation is no longer eligible for payment.");
     if(r.getParentReservationId()!=null&&!extensionPaymentWindow(r))throw conflict("An extension must be approved before the original reservation ends.");
    }
    case RESERVATION_ADJUSTMENT -> {
     var change=reservationChanges.byPayment(payment.getId()).orElseThrow(()->conflict("Payment has no associated reservation change."));
     var r=change.getReservation();
     if(change.getStatus()!=ReservationChangeStatus.PENDING_PAYMENT||r.getStatus()!=ReservationStatus.RESERVED)
      throw conflict("This reservation change is no longer eligible for payment.");
     if(!clock.instant().isBefore(r.getStartAt().minusSeconds(300)))throw conflict("The rescheduling payment must be approved at least five minutes before the reservation starts.");
    }
   case SUBSCRIPTION -> {
     var request=subscriptionRequest(payment);
     if(request.tenantId()!=null&&plans.isSpotPricedTenantPlan(request.plan())){
      var current=parkingSpots.byTenant(request.tenantId()).stream().map(ParkingSpot::getId).map(UUID::toString).collect(java.util.stream.Collectors.toSet());
      if(!plans.licensedSpotIds(request.plan()).equals(current))throw conflict("The parking spots have changed. Request a new quote before processing the payment.");
     }
     requireNoOpenSubscription(payment.getProfileId(),request.tenantId());
    }
    case SUBSCRIPTION_ADJUSTMENT -> {
     var request=subscriptionAdjustmentRequest(payment);
     var subscription=subscriptions.findById(request.subscriptionId()).orElseThrow(()->missing("Subscription"));
     if(subscription.getTenantId()==null||!subscription.activeAt(clock.instant())||!clock.instant().isBefore(SubscriptionPlans.periodEnd(subscription)))
      throw conflict("The subscription no longer allows this adjustment.");
     if(!plans.isSpotPricedTenantPlan(subscription.getPlan()))throw conflict("The active plan does not charge per parking spot.");
     var licensed=plans.licensedSpotIds(subscription.getPlan());
     for(UUID spotId:request.spotIds()){
      var spot=parkingSpots.findById(spotId).orElseThrow(()->missing("Parking spot"));
      if(!spot.getTenantId().equals(subscription.getTenantId())||licensed.contains(spotId.toString()))throw conflict("This parking spot does not require this adjustment.");
     }
   }
   case ADDITIONAL_CHARGE -> {
    var c=charges.byPayment(payment.getId()).orElseThrow(()->conflict("Payment has no associated charge."));
    if(c.getStatus()!=AdditionalChargeStatus.PENDING)throw conflict("The charge is not pending.");
   }
   case REFUND -> throw conflict("Refunds are processed through the refunds resource.");
  }
 }
 @Transactional
 public DigitalPayment process(SpotgoUserPrincipal actor,UUID id,boolean retry) {
  return process(actor,id,retry,null);
 }
 @Transactional
 public DigitalPayment process(SpotgoUserPrincipal actor,UUID id,boolean retry,UUID retryTokenId) {
  var p=ownPaymentForProcessing(actor,id);
  if(p.getStatus()==PaymentStatus.APPROVED||p.getStatus()==PaymentStatus.CANCELLED)return p;
  if(retryTokenId!=null){
   if(!retry)throw conflict("Only a retry can change the payment method.");
   var replacement=ownToken(actor,retryTokenId);
   if(!replacement.getProfileId().equals(p.getProfileId()))throw new AccessDeniedException("The payment method belongs to another profile.");
   p.replaceToken(replacement);payments.saveAndFlush(p);
  }
  var previous=attempts.byPayment(id);var attempt=previous.get(previous.size()-1);
  if(p.getStatus()==PaymentStatus.RECONCILIATION_REQUIRED||attempt.getStatus()==PaymentAttemptStatus.UNKNOWN)
   throw conflict("The payment result is unknown. Reconcile it instead of submitting another charge.");
  if(attempt.getStatus()!=PaymentAttemptStatus.STARTED){
   if(!retry) return p;
   if(attempt.getNextAttemptAt()!=null&&clock.instant().isBefore(attempt.getNextAttemptAt()))throw conflict("The retry is not available yet.");
   attempt=attempts.saveAndFlush(new PaymentAttempt(p,attempt.getAttemptNumber()+1,clock.instant()));
  }
  if(p.getOperationType()==PaymentOperationType.SUBSCRIPTION){var request=subscriptionRequest(p);if(request.tenantId()!=null)access.tenant(actor,request.tenantId());else if(actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("The payment requires the DRIVER role.");}
  if(p.getOperationType()==PaymentOperationType.SUBSCRIPTION_ADJUSTMENT){var request=subscriptionAdjustmentRequest(p);var subscription=subscriptions.findById(request.subscriptionId()).orElseThrow(()->missing("Subscription"));access.tenant(actor,subscription.getTenantId());}
  requireOpenSource(p);
  if(p.getToken()==null||!p.getToken().usable(clock.instant()))throw conflict("The payment token is not enabled.");
  PaymentProvider.PaymentResult result;
  try{result=provider.charge(p.getId(),attempt.getAttemptNumber(),p.getToken().getProviderTokenRef(),p.getAmount(),p.getCurrency());}
  catch(BillingException e){if(e.getStatusCode().value()==503)throw e;result=new PaymentProvider.PaymentResult(PaymentStatus.RECONCILIATION_REQUIRED,null,"PROVIDER_EXCEPTION");}
  catch(RuntimeException e){result=new PaymentProvider.PaymentResult(PaymentStatus.RECONCILIATION_REQUIRED,null,"PROVIDER_EXCEPTION");}
  finish(p,attempt,result);return payments.saveAndFlush(p);
 }
 @Transactional
 public void reconcileSimulatedPayment(UUID id) {
  var current=payments.findById(id).orElse(null);
  if(current==null||current.getStatus()!=PaymentStatus.PENDING||current.getProviderOperationRef()==null
    ||!current.getProviderOperationRef().startsWith("simulated-charge:"))return;
  access.lockProfile(current.getProfileId());
  var payment=payments.lock(id).orElse(null);
  if(payment==null||payment.getStatus()!=PaymentStatus.PENDING||payment.getProviderOperationRef()==null
    ||!payment.getProviderOperationRef().startsWith("simulated-charge:"))return;
  var all=attempts.byPayment(id);if(all.isEmpty())return;
  var attempt=all.get(all.size()-1);
  var result=provider.reconcile(id,attempt.getAttemptNumber(),payment.getProviderOperationRef());
  finish(payment,attempt,result);payments.saveAndFlush(payment);
 }
 @Transactional
 public void reconcileSimulatedRefund(UUID id) {
  var current=refunds.findById(id).orElse(null);
  if(current==null||current.getStatus()!=RefundStatus.PROCESSING||current.getProviderOperationRef()==null
    ||!current.getProviderOperationRef().startsWith("simulated-refund:"))return;
  UUID paymentId=current.getPaymentId();var paymentCurrent=payments.findById(paymentId).orElse(null);
  if(paymentCurrent==null)return;
  access.lockProfile(paymentCurrent.getProfileId());payments.lock(paymentId);
  var refund=refunds.lock(id).orElse(null);
  if(refund==null||refund.getStatus()!=RefundStatus.PROCESSING||refund.getProviderOperationRef()==null
    ||!refund.getProviderOperationRef().startsWith("simulated-refund:"))return;
  var result=provider.reconcileRefund(id,refund.getProviderOperationRef());
  RefundStatus previous=refund.getStatus();refund.result(result.status(),result.operationReference(),clock.instant());
  refunds.saveAndFlush(refund);
  if(previous!=refund.getStatus())publishRefundEvent(refund);
 }
 @Transactional
 public DigitalPayment reconcile(SpotgoUserPrincipal actor,UUID id) {
  var p=ownPaymentForProcessing(actor,id);
  if(p.getStatus()!=PaymentStatus.RECONCILIATION_REQUIRED&&p.getStatus()!=PaymentStatus.PENDING)return p;
  var all=attempts.byPayment(id);var a=all.get(all.size()-1);
  var result=provider.reconcile(id,a.getAttemptNumber(),p.getProviderOperationRef());
  finish(p,a,result);return payments.saveAndFlush(p);
 }
 private void finish(DigitalPayment p,PaymentAttempt attempt,PaymentProvider.PaymentResult result) {
  PaymentStatus previousStatus=p.getStatus();
  if(result==null||result.status()==null)result=new PaymentProvider.PaymentResult(PaymentStatus.RECONCILIATION_REQUIRED,null,"EMPTY_PROVIDER_RESPONSE");
  PaymentStatus state=result.status();
  if(!Set.of(PaymentStatus.APPROVED,PaymentStatus.REJECTED,PaymentStatus.PENDING,PaymentStatus.RECONCILIATION_REQUIRED).contains(state))
   state=PaymentStatus.RECONCILIATION_REQUIRED;
  if(state==PaymentStatus.APPROVED&&result.operationReference()==null)state=PaymentStatus.RECONCILIATION_REQUIRED;
  var now=clock.instant();
  p.result(state,result.operationReference(),Set.of(PaymentOperationType.SUBSCRIPTION,PaymentOperationType.SUBSCRIPTION_ADJUSTMENT).contains(p.getOperationType())?p.getResultReference():"/api/v1/digital-payments/"+p.getId(),now);
  PaymentAttemptStatus outcome=switch(state){
   case APPROVED -> PaymentAttemptStatus.SUCCESS;
   case REJECTED -> PaymentAttemptStatus.DEFINITIVE_FAILURE;
   case PENDING -> PaymentAttemptStatus.UNKNOWN;
   default -> PaymentAttemptStatus.UNKNOWN;
  };
  attempt.finish(outcome,result.errorCode(),state==PaymentStatus.REJECTED?now.plusSeconds(retrySeconds):null,now);
  attempts.saveAndFlush(attempt);payments.saveAndFlush(p);
  if(state==PaymentStatus.APPROVED)approved(p);
  else if(state==PaymentStatus.REJECTED && p.getOperationType()==PaymentOperationType.ADDITIONAL_CHARGE)
   unpaid(charges.byPayment(p.getId()).orElseThrow(()->conflict("Charge is not associated.")));
  if(previousStatus!=p.getStatus())publishPaymentEvent(p);
 }
 private void publishPaymentEvent(DigitalPayment payment){
  PaymentStatus status=payment.getStatus();
  String type;
  if(status==PaymentStatus.APPROVED){
    type=switch(payment.getOperationType()){
     case RESERVATION -> "billing.reservation_payment.approved";
     case RESERVATION_ADJUSTMENT -> "billing.reservation_change.approved";
    case SUBSCRIPTION -> "billing.subscription.started";
    case SUBSCRIPTION_ADJUSTMENT -> "billing.subscription.adjusted";
    default -> "billing.payment.approved";
   };
  }else if(status==PaymentStatus.REJECTED)type="billing.payment.rejected";
  else type="billing.payment.status_changed";
   UUID tenantId=paymentTenantId(payment);
  Map<String,String> data=Map.of("payment_id",payment.getId().toString(),"operation_type",payment.getOperationType().name(),"status",status.name());
  domainEvents.publish(type,"Payments & Billing","DigitalPayment",payment.getId(),tenantId,
   List.of(payment.getProfile().getAccountId()),data);
 }
 private UUID paymentTenantId(DigitalPayment payment){
   return switch(payment.getOperationType()){
    case RESERVATION -> reservations.byPayment(payment.getId()).map(Reservation::getTenantId).orElse(null);
    case RESERVATION_ADJUSTMENT -> reservationChanges.byPayment(payment.getId()).map(c->c.getReservation().getTenantId()).orElse(null);
   case SUBSCRIPTION -> subscriptions.byPayment(payment.getId()).map(Subscription::getTenantId).orElseGet(()->subscriptionRequest(payment).tenantId());
   case SUBSCRIPTION_ADJUSTMENT -> subscriptions.findById(subscriptionAdjustmentRequest(payment).subscriptionId()).map(Subscription::getTenantId).orElse(null);
   case ADDITIONAL_CHARGE -> charges.byPayment(payment.getId()).map(charge->charge.getReservation().getTenantId()).orElse(null);
   case REFUND -> null;
  };
 }
 private void approved(DigitalPayment p) {
   switch(p.getOperationType()){
    case RESERVATION -> {
     var r=reservations.byPayment(p.getId()).orElseThrow(()->conflict("Payment has no associated reservation."));
     if(r.getStatus()==ReservationStatus.RESERVED||r.getStatus()==ReservationStatus.ACTIVE||r.getStatus()==ReservationStatus.COMPLETED||r.getStatus()==ReservationStatus.OVERSTAYED)return;
      boolean confirmed=r.getStatus()==ReservationStatus.PENDING_PAYMENT&&r.blocks(clock.instant())&&
       (r.getParentReservationId()==null||extensionPaymentWindow(r));
      if(confirmed)r.confirm(p.getId(),clock.instant());
       else {
        r.expire(clock.instant());if(r.getStatus()==ReservationStatus.PENDING_PAYMENT)r.cancel();
        requestRefundInternal(p,p.getAmount(),"LATE_RESERVATION_APPROVAL");
       }
     reservations.saveAndFlush(r);
      if(confirmed)domainEvents.publish("parking.reservation.confirmed","Parking Infrastructure","Reservation",r.getId(),r.getTenantId(),
       List.of(r.getProfile().getAccountId()),Map.of("reservation_id",r.getId().toString(),"payment_id",p.getId().toString()));
    }
    case RESERVATION_ADJUSTMENT -> applyReservationChange(p);
   case ADDITIONAL_CHARGE -> {
    var c=charges.byPayment(p.getId()).orElseThrow(()->conflict("Payment has no associated charge."));c.paid();charges.saveAndFlush(c);
    balances.byCharge(c.getId()).ifPresent(b->{b.regularize(clock.instant());balances.saveAndFlush(b);});
   }
   case SUBSCRIPTION -> {
    if(subscriptions.byPayment(p.getId()).isPresent())return;
    var request=subscriptionRequest(p);
    try{requireNoOpenSubscription(p.getProfileId(),request.tenantId());}
    catch(BillingException e){if(e.getStatusCode().value()!=409)throw e;requestRefundInternal(p,p.getAmount(),"DUPLICATE_SUBSCRIPTION_APPROVAL");return;}
    var tenant=request.tenantId()==null?null:tenants.findById(request.tenantId()).orElseThrow(()->missing("Tenant"));
    var s=subscriptions.saveAndFlush(new Subscription(tenant==null?p.getProfile():null,tenant,request.plan(),p,clock.instant()));
    if(tenant!=null&&p.getTokenId()!=null){tenant.setDefaultPaymentTokenId(p.getTokenId());tenants.saveAndFlush(tenant);}
    p.result(PaymentStatus.APPROVED,p.getProviderOperationRef(),"/api/v1/subscriptions/"+s.getId(),clock.instant());
   }
    case SUBSCRIPTION_ADJUSTMENT -> applySubscriptionAdjustment(p);
    case REFUND -> throw conflict("Incompatible payment operation.");
   }
   }
  private Reservation rootReservation(Reservation reservation){
   var current=reservation;
   while(current.getParentReservation()!=null)current=current.getParentReservation();
   return current;
  }
  private boolean extensionPaymentWindow(Reservation extension){
   var root=rootReservation(extension);var now=clock.instant();
   return root.getStatus()==ReservationStatus.ACTIVE&&now.isBefore(root.getEndAt());
  }
  private void applyReservationChange(DigitalPayment payment){
   var change=reservationChanges.lock(reservationChanges.byPayment(payment.getId())
     .orElseThrow(()->conflict("Payment has no associated reservation change.")).getId())
     .orElseThrow(()->missing("Reservation change"));
   var r=reservations.lock(change.getReservationId()).orElseThrow(()->missing("Reservation"));
   var now=clock.instant();
   if(change.getStatus()!=ReservationChangeStatus.PENDING_PAYMENT||r.getStatus()!=ReservationStatus.RESERVED||
      now.isAfter(r.getStartAt().minusSeconds(300))){
    change.supersede();reservationChanges.saveAndFlush(change);
    requestRefundInternal(payment,payment.getAmount(),"STALE_RESERVATION_CHANGE");return;
   }
   boolean spotConflict=reservations.overlappingSpot(r.getSpotId(),change.getStartAt(),change.getEndAt()).stream()
     .anyMatch(other->!other.getId().equals(r.getId())&&other.blocks(now));
   boolean vehicleConflict=reservations.overlappingVehicle(r.getVehicleId(),change.getStartAt(),change.getEndAt()).stream()
      .anyMatch(other->!other.getId().equals(r.getId())&&other.blocks(now));
   boolean pendingConflict=reservationChanges.overlapping(r.getSpotId(),change.getStartAt(),change.getEndAt(),ReservationChangeStatus.PENDING_PAYMENT).stream()
      .anyMatch(other->!other.getReservationId().equals(r.getId()));
   if(spotConflict||vehicleConflict||pendingConflict){
    change.supersede();reservationChanges.saveAndFlush(change);requestRefundInternal(payment,payment.getAmount(),"RESERVATION_CHANGE_CONFLICT");return;
   }
   r.reschedule(change.getStartAt(),change.getEndAt(),change.getAmount());change.paymentApproved();
   reservations.saveAndFlush(r);reservationChanges.saveAndFlush(change);
  }
 private void applySubscriptionAdjustment(DigitalPayment payment){
  var request=subscriptionAdjustmentRequest(payment);
  var subscription=subscriptions.lock(request.subscriptionId()).orElseThrow(()->missing("Subscription"));
  var now=clock.instant();
  if(!subscription.activeAt(now)||!now.isBefore(SubscriptionPlans.periodEnd(subscription))){
   requestRefundInternal(payment,payment.getAmount(),"LATE_SPOT_PRORATION_APPROVAL");
   return;
  }
  subscription.updatePlan(request.plan());subscriptions.saveAndFlush(subscription);
  for(UUID spotId:request.spotIds()){
   var spot=parkingSpots.lock(spotId).orElseThrow(()->missing("Parking spot"));
   if(!spot.getTenantId().equals(subscription.getTenantId()))throw conflict("The parking spot belongs to another tenant.");
   spot.setOperationalStatus(OperationalStatus.ACTIVE);parkingSpots.saveAndFlush(spot);
  }
  payment.result(PaymentStatus.APPROVED,payment.getProviderOperationRef(),"/api/v1/subscriptions/"+subscription.getId(),now);
 }
 public void requireApproved(UUID id,UUID profile,BigDecimal amount){
  var p=payments.findById(id).orElseThrow(()->missing("Payment"));
  if(p.getStatus()!=PaymentStatus.APPROVED||p.getOperationType()!=PaymentOperationType.RESERVATION||!p.getProfileId().equals(profile)||p.getAmount().compareTo(amount)!=0)
   throw conflict("The payment does not match an approved reservation for this driver and amount.");
 }
 @Transactional
  public void cancelled(Reservation r) {
   var paymentIds=new LinkedHashSet<UUID>();if(r.getPaymentId()!=null)paymentIds.add(r.getPaymentId());
   for(var change:reservationChanges.byReservation(r.getId())){
    if(change.getStatus()==ReservationChangeStatus.PENDING_PAYMENT||change.getStatus()==ReservationChangeStatus.REFUND_SCHEDULED){
     change.supersede();reservationChanges.saveAndFlush(change);
    }
    if(change.getPaymentId()!=null)paymentIds.add(change.getPaymentId());
   }
   for(UUID id:paymentIds){
    var p=payments.lock(id).orElseThrow(()->missing("Payment"));
    if(p.getStatus()==PaymentStatus.APPROVED){
     var committed=refunds.byPayment(p.getId()).stream().filter(rf->rf.getStatus()!=RefundStatus.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
     var remaining=p.getAmount().subtract(committed);if(remaining.signum()>0)requestRefundInternal(p,remaining,"RESERVATION_CANCELLED");
    }else if(p.getStatus()==PaymentStatus.INITIATED||p.getStatus()==PaymentStatus.REJECTED)
     p.result(PaymentStatus.CANCELLED,p.getProviderOperationRef(),"CANCELLED_WITH_RESERVATION",clock.instant());
   }
  }
 @Transactional
 public void completed(Reservation r,ParkingSession session) {
  if(r.getStatus()!=ReservationStatus.OVERSTAYED||session.getExitAt()==null)return;
  String evidence="parking-session:"+session.getId();
  if(charges.byReservation(r.getId()).stream().anyMatch(c->c.getReason().equals("OVERSTAY")&&c.getEvidenceRef().equals(evidence)))return;
  var from=r.getEndAt();long tolerance=ParkingTariffs.toleranceMinutes(r.getTenant().getRules());
  if(afterTolerance)from=from.plusSeconds(tolerance*60);
  if(!session.getExitAt().isAfter(from))return;
  var amount=ParkingTariffs.price(r.getTenant().getRules(),from,session.getExitAt());
  if(amount.signum()==0)return;
  var c=charges.saveAndFlush(new AdditionalCharge(r,amount,"OVERSTAY",evidence,clock.instant()));unpaid(c);
 }
 @Transactional
 public void expirePeriods(){
  var now=clock.instant();
  for(var current:subscriptions.findAll()) if(current.getEndAt()==null&&!now.isBefore(SubscriptionPlans.periodEnd(current))){
   if(current.getProfileId()!=null)access.lockProfile(current.getProfileId());else tenants.lock(current.getTenantId()).orElseThrow(()->missing("Tenant"));
   var s=subscriptions.lock(current.getId()).orElseThrow(()->missing("Subscription"));
   if(s.getEndAt()==null&&!now.isBefore(SubscriptionPlans.periodEnd(s)))s.expire(SubscriptionPlans.periodEnd(s));
  }
 }
 private Refund requestRefundInternal(DigitalPayment p,BigDecimal amount,String reason) {
  if(p.getStatus()!=PaymentStatus.APPROVED)throw conflict("Only approved payments can be refunded.");
  var old=refunds.byPayment(p.getId());
  var replay=old.stream().filter(r->r.getAmount().compareTo(amount)==0&&r.getReason().equals(text(reason))).findFirst();
  if(replay.isPresent())return replay.get();
  BigDecimal committed=old.stream().filter(r->r.getStatus()!=RefundStatus.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(positive(amount).add(committed).compareTo(p.getAmount())>0)throw conflict("The total refunded or requested exceeds the original payment amount.");
  return refunds.saveAndFlush(new Refund(p,amount,reason,clock.instant()));
 }
 @Transactional
 public Refund requestRefund(SpotgoUserPrincipal actor,UUID payment,BigDecimal amount,String reason) {
  var p=payments.findById(payment).orElseThrow(()->missing("Payment"));access.payment(actor,p);
  if(actor.getRole()!=UserRole.SUPER_ADMIN&&actor.getRole()!=UserRole.PARKING_ADMIN)throw new AccessDeniedException("The administrator must authorize the refund.");
  access.lockProfile(p.getProfileId());p=payments.lock(payment).orElseThrow(()->missing("Payment"));return requestRefundInternal(p,amount,reason);
 }
 @Transactional
  public Refund processRefund(SpotgoUserPrincipal actor,UUID id) {
  var current=refunds.findById(id).orElseThrow(()->missing("Refund"));access.payment(actor,current.getPayment());
  if(actor.getRole()!=UserRole.SUPER_ADMIN&&actor.getRole()!=UserRole.PARKING_ADMIN)throw new AccessDeniedException("Only an authorized administrator can perform this action.");
   access.lockProfile(current.getPayment().getProfileId());payments.lock(current.getPaymentId());
   var r=refunds.lock(id).orElseThrow(()->missing("Refund"));
   return processRefundLocked(r);
  }
  private Refund processRefundLocked(Refund r) {
   if(r.getStatus()==RefundStatus.COMPLETED)return r;
   BigDecimal otherRefunds=refunds.byPayment(r.getPaymentId()).stream().filter(other->!other.getId().equals(r.getId())&&other.getStatus()!=RefundStatus.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(otherRefunds.add(r.getAmount()).compareTo(r.getPayment().getAmount())>0)throw conflict("The refundable amount has already been used.");
  PaymentProvider.RefundResult result;
  try{
   if(r.getStatus()==RefundStatus.PROCESSING||r.getStatus()==RefundStatus.RECONCILIATION_REQUIRED)
    result=provider.reconcileRefund(r.getId(),r.getProviderOperationRef());
   else result=provider.refund(r.getId(),r.getPayment().getProviderOperationRef(),r.getAmount(),r.getPayment().getCurrency());
  }
  catch(BillingException e){if(e.getStatusCode().value()==503)throw e;result=new PaymentProvider.RefundResult(RefundStatus.RECONCILIATION_REQUIRED,null);}
  catch(RuntimeException e){result=new PaymentProvider.RefundResult(RefundStatus.RECONCILIATION_REQUIRED,null);}
  if(result==null||result.status()==null)throw conflict("Invalid refund response.");
  if(result.status()==RefundStatus.COMPLETED&&result.operationReference()==null)throw conflict("Refund has no verifiable provider reference.");
  RefundStatus previous=r.getStatus();r.result(result.status(),result.operationReference(),clock.instant());refunds.saveAndFlush(r);
   if(previous!=r.getStatus())publishRefundEvent(r);return r;
  }
  @Transactional
  public void processReservationChangeRefunds() {
   var now=clock.instant();
   for(var candidate:reservationChanges.dueRefunds(ReservationChangeStatus.REFUND_SCHEDULED,now)){
    var change=reservationChanges.lock(candidate.getId()).orElse(null);
    if(change==null||change.getStatus()!=ReservationChangeStatus.REFUND_SCHEDULED||change.getRefundDueAt()==null||now.isBefore(change.getRefundDueAt()))continue;
    var reservation=reservations.lock(change.getReservationId()).orElseThrow(()->missing("Reservation"));
    access.lockProfile(reservation.getProfileId());
    var history=reservationChanges.byReservation(reservation.getId());
    BigDecimal refundable=reservationPaidBalance(reservation,history).subtract(change.getAmount()).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
    if(refundable.signum()==0){change.refundResult(true,false);reservationChanges.saveAndFlush(change);continue;}
    var candidates=new ArrayList<DigitalPayment>();
    if(reservation.getPaymentId()!=null)payments.findById(reservation.getPaymentId()).filter(p->p.getStatus()==PaymentStatus.APPROVED).ifPresent(candidates::add);
    history.stream().filter(c->c.getPaymentId()!=null).sorted(Comparator.comparing(ReservationChange::getCreatedAt).reversed())
      .forEach(c->payments.findById(c.getPaymentId()).filter(p->p.getStatus()==PaymentStatus.APPROVED).ifPresent(candidates::add));
    BigDecimal remaining=refundable;
    for(var payment:candidates){
     if(remaining.signum()<=0)break;
     BigDecimal committed=refunds.byPayment(payment.getId()).stream().filter(r->r.getStatus()!=RefundStatus.FAILED)
       .map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
     BigDecimal capacity=payment.getAmount().subtract(committed).max(BigDecimal.ZERO);
     BigDecimal slice=capacity.min(remaining).setScale(2,RoundingMode.HALF_UP);
     if(slice.signum()>0)refunds.saveAndFlush(new Refund(payment,slice,"RESCHEDULE_REDUCTION",now,change));
     remaining=remaining.subtract(slice);
    }
    if(remaining.signum()>0)throw conflict("The reservation refund exceeds the remaining captured amount.");
    change.refundRequested();reservationChanges.saveAndFlush(change);
   }
   for(var candidate:reservationChanges.findByStatus(ReservationChangeStatus.REFUND_REQUESTED)){
    var change=reservationChanges.lock(candidate.getId()).orElse(null);
    if(change==null||change.getStatus()!=ReservationChangeStatus.REFUND_REQUESTED)continue;
    var reservation=reservations.lock(change.getReservationId()).orElseThrow(()->missing("Reservation"));
    access.lockProfile(reservation.getProfileId());
    var associated=refunds.byReservationChange(change.getId());
    for(var item:associated){
     if(!Set.of(RefundStatus.REQUESTED,RefundStatus.PROCESSING,RefundStatus.RECONCILIATION_REQUIRED).contains(item.getStatus()))continue;
     payments.lock(item.getPaymentId());var locked=refunds.lock(item.getId()).orElseThrow(()->missing("Refund"));processRefundLocked(locked);
    }
    associated=refunds.byReservationChange(change.getId());
    boolean completed=!associated.isEmpty()&&associated.stream().allMatch(r->r.getStatus()==RefundStatus.COMPLETED);
    boolean failed=associated.stream().anyMatch(r->r.getStatus()==RefundStatus.FAILED);
    if(completed||failed){change.refundResult(completed,failed);reservationChanges.saveAndFlush(change);}
   }
  }
 private void publishRefundEvent(Refund refund){
  var payment=refund.getPayment();
  domainEvents.publish("billing.refund.status_changed","Payments & Billing","Refund",refund.getId(),
   paymentTenantId(payment),List.of(payment.getProfile().getAccountId()),
   Map.of("refund_id",refund.getId().toString(),"payment_id",payment.getId().toString(),"status",refund.getStatus().name()));
 }
 @Transactional
 public BillingDocument issueDocument(SpotgoUserPrincipal actor,UUID payment,BillingDocumentType type,String number,String series,String legalName,Map<String,Object> tax) {
  var p=payments.findById(payment).orElseThrow(()->missing("Payment"));access.payment(actor,p);
  if(actor.getRole()!=UserRole.PARKING_ADMIN&&actor.getRole()!=UserRole.SUPER_ADMIN)throw new AccessDeniedException("Only the authorized issuer can register billing documents.");
  access.lockProfile(p.getProfileId());p=payments.lock(payment).orElseThrow(()->missing("Payment"));
  String doc=Objects.toString(tax.get("document_number"),"");
  String expected=type==BillingDocumentType.ELECTRONIC_RECEIPT?"DNI":"RUC";
  if(!expected.equals(tax.get("document_type"))||!doc.matches(type==BillingDocumentType.ELECTRONIC_RECEIPT?"[0-9]{8}":"[0-9]{11}"))throw new IllegalArgumentException("Tax information does not match the billing document type.");
  var existing=documents.byPayment(payment);
  if(existing.isPresent()){
   var d=existing.get();
   if(d.getDocumentType()!=type||!d.getDocumentNumber().equals(number)||!d.getSeries().equals(series)||!d.getLegalName().equals(legalName)||!d.getTaxData().equals(tax))throw conflict("A different billing document is already registered for this payment.");
   return d;
  }
  return documents.saveAndFlush(new BillingDocument(p,type,number,series,legalName,tax,clock.instant()));
 }
 @Transactional
 public BillingDocument voidDocument(SpotgoUserPrincipal actor,UUID id){
  var d=documents.findById(id).orElseThrow(()->missing("Billing document"));access.payment(actor,d.getPayment());
  if(actor.getRole()!=UserRole.PARKING_ADMIN&&actor.getRole()!=UserRole.SUPER_ADMIN)throw new AccessDeniedException("Only the authorized issuer can perform this action.");
  payments.lock(d.getPaymentId());d.voidDocument();return documents.saveAndFlush(d);
 }
}
