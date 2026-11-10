package com.yachiqo.spotgo.billing.application;
import com.yachiqo.spotgo.billing.domain.model.aggregates.*;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.*;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.application.ParkingTariffs;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.*;
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
 private final TenantRepository tenants;
 private final BillingAccess access;
 private final SubscriptionPlans plans;
 private final PaymentProvider provider;
 private final Clock clock;
 private final int retrySeconds;
 private final int debtHours;
 private final boolean afterTolerance;
 public BillingService(PaymentTokenRepository tokens,DigitalPaymentRepository payments,PaymentAttemptRepository attempts,
  SubscriptionRepository subscriptions,AdditionalChargeRepository charges,OutstandingBalanceRepository balances,
  RefundRepository refunds,BillingDocumentRepository documents,ReservationRepository reservations,TenantRepository tenants,
  BillingAccess access,SubscriptionPlans plans,PaymentProvider provider,Clock clock,
  @Value("${app.billing.retry-delay-seconds:60}") int retrySeconds,
  @Value("${app.billing.debt-due-hours:24}") int debtHours,
  @Value("${app.billing.overstay-charge-basis:AFTER_TOLERANCE}") String basis) {
  this.tokens=tokens;this.payments=payments;this.attempts=attempts;this.subscriptions=subscriptions;this.charges=charges;
  this.balances=balances;this.refunds=refunds;this.documents=documents;this.reservations=reservations;this.tenants=tenants;
  this.access=access;this.plans=plans;this.provider=provider;this.clock=clock;this.retrySeconds=retrySeconds;this.debtHours=debtHours;
  if(retrySeconds<1||debtHours<1||!Set.of("AFTER_TOLERANCE","TOTAL_EXCESS").contains(basis))throw new IllegalArgumentException("Politica de cobros invalida");
  afterTolerance=basis.equals("AFTER_TOLERANCE");
 }
 public static BillingException missing(String resource){return new BillingException(HttpStatus.NOT_FOUND,resource+" inexistente");}
 public static BillingException conflict(String reason){return new BillingException(HttpStatus.CONFLICT,reason);}
 @Transactional
 public PaymentToken registerToken(SpotgoUserPrincipal actor,String reference) {
  var profile=access.profile(actor);access.lockProfile(profile.getId());
  var details=provider.inspectToken(text(reference));
  var token=new PaymentToken(profile,details.reference(),details.brand(),details.lastFour(),details.expirationMonth(),details.expirationYear(),clock.instant());
  if(!token.usable(clock.instant()))throw new IllegalArgumentException("Medio de pago vencido");
  return tokens.saveAndFlush(token);
 }
 @Transactional
 public PaymentToken revokeToken(SpotgoUserPrincipal actor,UUID id){
  var t=tokens.lock(id).orElseThrow(()->missing("Token de pago"));access.owner(actor,t.getProfile());t.revoke(clock.instant());return tokens.saveAndFlush(t);
 }
 public void lockProfile(UUID profile){access.lockProfile(profile);}
 public void requireNoDebt(UUID profile){
  if(balances.byProfile(profile).stream().anyMatch(b->b.getStatus()==OutstandingBalanceStatus.OPEN&&b.getAmount().signum()>0))
   throw conflict("Debes regularizar tus deudas antes de crear otra reserva; puedes consultar y pagar tus deudas");
 }
 public BigDecimal reservationPrice(UUID profile,BigDecimal base,Instant now) {
  var active=subscriptions.byProfile(profile).stream().filter(s->s.activeAt(now)&&now.isBefore(SubscriptionPlans.periodEnd(s))).toList();
  if(active.size()>1)throw conflict("Existe mas de una membresia vigente; revisa la configuracion del titular");
  BigDecimal discount=active.isEmpty()?BigDecimal.ZERO:new BigDecimal(active.get(0).getPlan().getOrDefault("discount_percent",0).toString());
  if(discount.signum()<0||discount.compareTo(BigDecimal.valueOf(100))>=0)throw conflict("Descuento de membresia invalido");
  return base.multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2))).setScale(2,RoundingMode.HALF_UP);
 }
 private PaymentToken ownToken(SpotgoUserPrincipal actor,UUID id){
  if(id==null)throw new IllegalArgumentException("Selecciona un token de pago");
  var token=tokens.findById(id).orElseThrow(()->missing("Token de pago"));access.owner(actor,token.getProfile());
  if(!token.usable(clock.instant()))throw conflict("Token de pago revocado o vencido");return token;
 }
 private DigitalPayment existing(String key,String hash,Profile profile) {
  var payment=payments.findByIdempotencyKey(text(key)).orElse(null);
  if(payment==null)return null;
  if(!payment.getProfileId().equals(profile.getId()))throw new AccessDeniedException("Clave de operacion de otro titular");
  if(!payment.getRequestHash().equals(hash))throw conflict("La clave de idempotencia ya fue utilizada con otros datos");
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
  var profile=access.profile(actor);if(actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("Solo el conductor paga su reserva");
  access.lockProfile(profile.getId());
  var r=reservations.lock(reservationId).orElseThrow(()->missing("Reserva"));access.owner(actor,r.getProfile());
  String hash=hash(PaymentOperationType.RESERVATION,reservationId,tokenId,profile,r.getAmount());
  var replay=existing(key,hash,profile);if(replay!=null)return replay;
  if(r.getPaymentId()!=null)throw conflict("La reserva ya tiene una operacion de pago: consulta o reintenta esa operacion");
  if(r.getStatus()!=ReservationStatus.PENDING_PAYMENT||!r.blocks(clock.instant()))throw conflict("El bloqueo de la reserva no esta vigente");
  var payment=create(profile,ownToken(actor,tokenId),PaymentOperationType.RESERVATION,r.getAmount(),key,hash);
  r.attachPayment(payment.getId());reservations.saveAndFlush(r);return payment;
 }
 @Transactional
 public DigitalPayment subscriptionPayment(SpotgoUserPrincipal actor,String code,UUID tokenId,String key) {
  var profile=access.profile(actor);access.lockProfile(profile.getId());
  // A replay uses its original snapshot even if the server catalog changes later.
  var replay=payments.findByIdempotencyKey(text(key)).orElse(null);
  if(replay!=null){
   access.owner(actor,replay.getProfile());
   var request=subscriptionRequest(replay);
   if(!Objects.equals(request.plan().get("code"),code)||!Objects.equals(replay.getTokenId(),tokenId)||!Objects.equals(request.tenantId(),actor.getRole()==UserRole.PARKING_ADMIN?actor.getTenantId():null))throw conflict("La clave ya tiene otra solicitud");
   replay.seen(clock.instant());return payments.saveAndFlush(replay);
  }
  var plan=plans.get(code);boolean driver=actor.getRole()==UserRole.DRIVER;
  if(!plan.get("audience").equals(driver?"DRIVER":"PARKING_ADMIN"))throw new AccessDeniedException("El plan no corresponde a tu rol");
  UUID tenantId=driver?null:actor.getTenantId();if(!driver)access.tenant(actor,tenantId);
  requireNoOpenSubscription(profile.getId(),tenantId);
  String reference=encodeSubscriptionRequest(plan,tenantId);
  BigDecimal amount=positive(new BigDecimal(plan.get("price").toString()));
  var p=create(profile,ownToken(actor,tokenId),PaymentOperationType.SUBSCRIPTION,amount,key,hash(PaymentOperationType.SUBSCRIPTION,tenantId,tokenId,profile,amount));
  p.subscriptionRequest(reference);return payments.saveAndFlush(p);
 }
 private record SubscriptionRequest(Map<String,Object> plan,UUID tenantId) {}
 private String encodeSubscriptionRequest(Map<String,Object> plan,UUID tenant){
  try{return "subscription-request:"+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("plan",plan,"tenant",tenant==null?"":tenant.toString()));}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Plan invalido",e);}
 }
 private SubscriptionRequest subscriptionRequest(DigitalPayment p){
  var fulfilled=subscriptions.byPayment(p.getId());if(fulfilled.isPresent())return new SubscriptionRequest(fulfilled.get().getPlan(),fulfilled.get().getTenantId());
  if(p.getOperationType()!=PaymentOperationType.SUBSCRIPTION||!p.getResultReference().startsWith("subscription-request:"))throw conflict("Pago sin solicitud de suscripcion");
  try{
   var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var node=mapper.readTree(p.getResultReference().substring("subscription-request:".length()));
   Map<String,Object> plan=mapper.convertValue(node.get("plan"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
   String tenant=node.path("tenant").asText();return new SubscriptionRequest(plan,tenant.isEmpty()?null:UUID.fromString(tenant));
  }catch(Exception e){throw conflict("Solicitud de suscripcion invalida");}
 }
 private void requireNoOpenSubscription(UUID profile,UUID tenant){
  if(tenant!=null)tenants.lock(tenant).orElseThrow(()->missing("Tenant"));
  var old=tenant==null?subscriptions.byProfile(profile):subscriptions.byTenant(tenant);
  for(var previous:old)if(previous.getEndAt()==null){
   var end=SubscriptionPlans.periodEnd(previous);
   if(clock.instant().isBefore(end))throw conflict("El titular ya tiene un periodo vigente");
   previous.expire(end);
  }
 }
 @Transactional
 public Subscription cancelSubscription(SpotgoUserPrincipal actor,UUID id) {
  var current=subscriptions.findById(id).orElseThrow(()->missing("Suscripcion"));access.subscription(actor,current);
  access.lockProfile(access.profile(actor).getId());
  var s=subscriptions.lock(id).orElseThrow(()->missing("Suscripcion"));access.subscription(actor,s);
  if(s.getEndAt()==null)s.close(clock.instant());return subscriptions.saveAndFlush(s);
 }
 @Transactional
 public AdditionalCharge createCharge(SpotgoUserPrincipal actor,UUID reservationId,BigDecimal amount,String reason,String evidence) {
  var current=reservations.findById(reservationId).orElseThrow(()->missing("Reserva"));access.tenant(actor,current.getTenantId());access.lockProfile(current.getProfileId());
  var r=reservations.lock(reservationId).orElseThrow(()->missing("Reserva"));
  if(!Set.of(ReservationStatus.ACTIVE,ReservationStatus.COMPLETED,ReservationStatus.OVERSTAYED).contains(r.getStatus()))
   throw conflict("El cargo requiere una reserva utilizada");
  var duplicate=charges.byReservation(reservationId).stream().filter(c->c.getReason().equals(text(reason))&&c.getEvidenceRef().equals(text(evidence))).findFirst();
  if(duplicate.isPresent()){
   if(duplicate.get().getAmount().compareTo(amount)!=0)throw conflict("La misma evidencia ya tiene un cargo con otro importe");
   return duplicate.get();
  }
  return charges.saveAndFlush(new AdditionalCharge(r,amount,reason,evidence,clock.instant()));
 }
 private OutstandingBalance unpaid(AdditionalCharge c) {
  if(c.getStatus()==AdditionalChargeStatus.PAID)throw conflict("El cargo ya esta pagado");
  if(c.getPayment()!=null && c.getPayment().getStatus()!=PaymentStatus.REJECTED)
   throw conflict("Espera un resultado definitivo del pago antes de declarar la deuda");
  return balances.byCharge(c.getId()).orElseGet(()->balances.saveAndFlush(new OutstandingBalance(c,clock.instant().plusSeconds(debtHours*3600L),clock.instant())));
 }
 @Transactional
 public OutstandingBalance markUnpaid(SpotgoUserPrincipal actor,UUID chargeId) {
  var current=charges.findById(chargeId).orElseThrow(()->missing("Cargo"));access.tenant(actor,current.getReservation().getTenantId());access.lockProfile(current.getReservation().getProfileId());
  return unpaid(charges.lock(chargeId).orElseThrow(()->missing("Cargo")));
 }
 @Transactional
 public DigitalPayment chargePayment(SpotgoUserPrincipal actor,UUID chargeId,UUID tokenId,String key) {
  var profile=access.profile(actor);if(actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("Solo el conductor paga sus cargos");
  access.lockProfile(profile.getId());
  var c=charges.lock(chargeId).orElseThrow(()->missing("Cargo"));access.owner(actor,c.getReservation().getProfile());
  String hash=hash(PaymentOperationType.ADDITIONAL_CHARGE,chargeId,tokenId,profile,c.getAmount());
  var replay=existing(key,hash,profile);if(replay!=null)return replay;
  if(c.getStatus()!=AdditionalChargeStatus.PENDING||c.getPayment()!=null)throw conflict("El cargo ya tiene una operacion; utiliza su reintento");
  var p=create(profile,ownToken(actor,tokenId),PaymentOperationType.ADDITIONAL_CHARGE,c.getAmount(),key,hash);
  c.attach(p);charges.saveAndFlush(c);return p;
 }
 private DigitalPayment ownPaymentForProcessing(SpotgoUserPrincipal actor,UUID id){
  var current=payments.findById(id).orElseThrow(()->missing("Pago"));access.owner(actor,current.getProfile());
  access.lockProfile(current.getProfileId());
  return payments.lock(id).orElseThrow(()->missing("Pago"));
 }
 private void requireOpenSource(DigitalPayment payment){
  switch(payment.getOperationType()){
   case RESERVATION -> {
    var r=reservations.byPayment(payment.getId()).orElseThrow(()->conflict("Pago sin reserva asociada"));
    if(r.getStatus()!=ReservationStatus.PENDING_PAYMENT||!r.blocks(clock.instant()))throw conflict("La reserva ya no permite cobrar");
   }
   case SUBSCRIPTION -> {
    var request=subscriptionRequest(payment);requireNoOpenSubscription(payment.getProfileId(),request.tenantId());
   }
   case ADDITIONAL_CHARGE -> {
    var c=charges.byPayment(payment.getId()).orElseThrow(()->conflict("Pago sin cargo asociado"));
    if(c.getStatus()!=AdditionalChargeStatus.PENDING)throw conflict("El cargo no esta pendiente");
   }
   case REFUND -> throw conflict("Los reembolsos se procesan en refunds");
  }
 }
 @Transactional
 public DigitalPayment process(SpotgoUserPrincipal actor,UUID id,boolean retry) {
  var p=ownPaymentForProcessing(actor,id);
  if(p.getStatus()==PaymentStatus.APPROVED||p.getStatus()==PaymentStatus.CANCELLED)return p;
  var previous=attempts.byPayment(id);var attempt=previous.get(previous.size()-1);
  if(p.getStatus()==PaymentStatus.RECONCILIATION_REQUIRED||attempt.getStatus()==PaymentAttemptStatus.UNKNOWN)
   throw conflict("Resultado desconocido: usa reconcile, no vuelvas a cobrar");
  if(attempt.getStatus()!=PaymentAttemptStatus.STARTED){
   if(!retry) return p;
   if(attempt.getNextAttemptAt()!=null&&clock.instant().isBefore(attempt.getNextAttemptAt()))throw conflict("Aun no corresponde el reintento");
   attempt=attempts.saveAndFlush(new PaymentAttempt(p,attempt.getAttemptNumber()+1,clock.instant()));
  }
  if(p.getOperationType()==PaymentOperationType.SUBSCRIPTION){var request=subscriptionRequest(p);if(request.tenantId()!=null)access.tenant(actor,request.tenantId());else if(actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("El pago requiere rol DRIVER");}
  requireOpenSource(p);
  if(p.getToken()==null||!p.getToken().usable(clock.instant()))throw conflict("El token de pago no esta habilitado");
  p.pending();
  PaymentProvider.PaymentResult result;
  try{result=provider.charge(p.getId(),attempt.getAttemptNumber(),p.getToken().getProviderTokenRef(),p.getAmount(),p.getCurrency());}
  catch(BillingException e){if(e.getStatusCode().value()==503)throw e;result=new PaymentProvider.PaymentResult(PaymentStatus.RECONCILIATION_REQUIRED,null,"PROVIDER_EXCEPTION");}
  catch(RuntimeException e){result=new PaymentProvider.PaymentResult(PaymentStatus.RECONCILIATION_REQUIRED,null,"PROVIDER_EXCEPTION");}
  finish(p,attempt,result);return payments.saveAndFlush(p);
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
  if(result==null||result.status()==null)result=new PaymentProvider.PaymentResult(PaymentStatus.RECONCILIATION_REQUIRED,null,"EMPTY_PROVIDER_RESPONSE");
  PaymentStatus state=result.status();
  if(!Set.of(PaymentStatus.APPROVED,PaymentStatus.REJECTED,PaymentStatus.PENDING,PaymentStatus.RECONCILIATION_REQUIRED).contains(state))
   state=PaymentStatus.RECONCILIATION_REQUIRED;
  if(state==PaymentStatus.APPROVED&&result.operationReference()==null)state=PaymentStatus.RECONCILIATION_REQUIRED;
  var now=clock.instant();
  p.result(state,result.operationReference(),p.getOperationType()==PaymentOperationType.SUBSCRIPTION?p.getResultReference():"/api/v1/digital-payments/"+p.getId(),now);
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
   unpaid(charges.byPayment(p.getId()).orElseThrow(()->conflict("Cargo no asociado")));
 }
 private void approved(DigitalPayment p) {
  switch(p.getOperationType()){
   case RESERVATION -> {
    var r=reservations.byPayment(p.getId()).orElseThrow(()->conflict("Pago sin reserva"));
    if(r.getStatus()==ReservationStatus.RESERVED||r.getStatus()==ReservationStatus.ACTIVE||r.getStatus()==ReservationStatus.COMPLETED||r.getStatus()==ReservationStatus.OVERSTAYED)return;
    if(r.getStatus()==ReservationStatus.PENDING_PAYMENT&&r.blocks(clock.instant()))r.confirm(p.getId(),clock.instant());
    else {r.expire(clock.instant());requestRefundInternal(p,p.getAmount(),"LATE_RESERVATION_APPROVAL");}
    reservations.saveAndFlush(r);
   }
   case ADDITIONAL_CHARGE -> {
    var c=charges.byPayment(p.getId()).orElseThrow(()->conflict("Pago sin cargo"));c.paid();charges.saveAndFlush(c);
    balances.byCharge(c.getId()).ifPresent(b->{b.regularize(clock.instant());balances.saveAndFlush(b);});
   }
   case SUBSCRIPTION -> {
    if(subscriptions.byPayment(p.getId()).isPresent())return;
    var request=subscriptionRequest(p);
    try{requireNoOpenSubscription(p.getProfileId(),request.tenantId());}
    catch(BillingException e){if(e.getStatusCode().value()!=409)throw e;requestRefundInternal(p,p.getAmount(),"DUPLICATE_SUBSCRIPTION_APPROVAL");return;}
    var tenant=request.tenantId()==null?null:tenants.findById(request.tenantId()).orElseThrow(()->missing("Tenant"));
    var s=subscriptions.saveAndFlush(new Subscription(tenant==null?p.getProfile():null,tenant,request.plan(),p,clock.instant()));
    p.result(PaymentStatus.APPROVED,p.getProviderOperationRef(),"/api/v1/subscriptions/"+s.getId(),clock.instant());
   }
   case REFUND -> throw conflict("Operacion de pago incompatible");
  }
 }
 public void requireApproved(UUID id,UUID profile,BigDecimal amount){
  var p=payments.findById(id).orElseThrow(()->missing("Pago"));
  if(p.getStatus()!=PaymentStatus.APPROVED||p.getOperationType()!=PaymentOperationType.RESERVATION||!p.getProfileId().equals(profile)||p.getAmount().compareTo(amount)!=0)
   throw conflict("El pago no corresponde a una reserva aprobada del conductor por ese importe");
 }
 @Transactional
 public void cancelled(Reservation r) {
  if(r.getPaymentId()==null)return;
  var p=payments.lock(r.getPaymentId()).orElseThrow(()->missing("Pago"));
  if(p.getStatus()==PaymentStatus.APPROVED){
   var committed=refunds.byPayment(p.getId()).stream().filter(rf->rf.getStatus()!=RefundStatus.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
   var remaining=p.getAmount().subtract(committed);if(remaining.signum()>0)requestRefundInternal(p,remaining,"RESERVATION_CANCELLED");
  }
  else if(p.getStatus()==PaymentStatus.INITIATED||p.getStatus()==PaymentStatus.REJECTED)p.result(PaymentStatus.CANCELLED,p.getProviderOperationRef(),"CANCELLED_WITH_RESERVATION",clock.instant());
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
   var s=subscriptions.lock(current.getId()).orElseThrow(()->missing("Suscripcion"));
   if(s.getEndAt()==null&&!now.isBefore(SubscriptionPlans.periodEnd(s)))s.expire(SubscriptionPlans.periodEnd(s));
  }
 }
 private Refund requestRefundInternal(DigitalPayment p,BigDecimal amount,String reason) {
  if(p.getStatus()!=PaymentStatus.APPROVED)throw conflict("Solo se reembolsa un pago aprobado");
  var old=refunds.byPayment(p.getId());
  var replay=old.stream().filter(r->r.getAmount().compareTo(amount)==0&&r.getReason().equals(text(reason))).findFirst();
  if(replay.isPresent())return replay.get();
  BigDecimal committed=old.stream().filter(r->r.getStatus()!=RefundStatus.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(positive(amount).add(committed).compareTo(p.getAmount())>0)throw conflict("El total devuelto o solicitado supera el pago original");
  return refunds.saveAndFlush(new Refund(p,amount,reason,clock.instant()));
 }
 @Transactional
 public Refund requestRefund(SpotgoUserPrincipal actor,UUID payment,BigDecimal amount,String reason) {
  var p=payments.findById(payment).orElseThrow(()->missing("Pago"));access.payment(actor,p);
  if(actor.getRole()!=UserRole.SUPER_ADMIN&&actor.getRole()!=UserRole.PARKING_ADMIN)throw new AccessDeniedException("El administrador debe autorizar el reembolso");
  access.lockProfile(p.getProfileId());p=payments.lock(payment).orElseThrow(()->missing("Pago"));return requestRefundInternal(p,amount,reason);
 }
 @Transactional
 public Refund processRefund(SpotgoUserPrincipal actor,UUID id) {
  var current=refunds.findById(id).orElseThrow(()->missing("Reembolso"));access.payment(actor,current.getPayment());
  if(actor.getRole()!=UserRole.SUPER_ADMIN&&actor.getRole()!=UserRole.PARKING_ADMIN)throw new AccessDeniedException("Solo administrador autorizado");
  access.lockProfile(current.getPayment().getProfileId());payments.lock(current.getPaymentId());
  var r=refunds.lock(id).orElseThrow(()->missing("Reembolso"));
  if(r.getStatus()==RefundStatus.COMPLETED)return r;
  BigDecimal otherRefunds=refunds.byPayment(r.getPaymentId()).stream().filter(other->!other.getId().equals(r.getId())&&other.getStatus()!=RefundStatus.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(otherRefunds.add(r.getAmount()).compareTo(r.getPayment().getAmount())>0)throw conflict("El presupuesto reembolsable ya fue utilizado");
  PaymentProvider.RefundResult result;
  try{result=provider.refund(r.getId(),r.getPayment().getProviderOperationRef(),r.getAmount(),r.getPayment().getCurrency());}
  catch(BillingException e){if(e.getStatusCode().value()==503)throw e;result=new PaymentProvider.RefundResult(RefundStatus.RECONCILIATION_REQUIRED,null);}
  catch(RuntimeException e){result=new PaymentProvider.RefundResult(RefundStatus.RECONCILIATION_REQUIRED,null);}
  if(result==null||result.status()==null)throw conflict("Respuesta de reembolso invalida");
  if(result.status()==RefundStatus.COMPLETED&&result.operationReference()==null)throw conflict("Reembolso sin referencia verificable");
  r.result(result.status(),result.operationReference(),clock.instant());return refunds.saveAndFlush(r);
 }
 @Transactional
 public BillingDocument issueDocument(SpotgoUserPrincipal actor,UUID payment,BillingDocumentType type,String number,String series,String legalName,Map<String,Object> tax) {
  var p=payments.findById(payment).orElseThrow(()->missing("Pago"));access.payment(actor,p);
  if(actor.getRole()!=UserRole.PARKING_ADMIN&&actor.getRole()!=UserRole.SUPER_ADMIN)throw new AccessDeniedException("Solo el emisor autorizado registra comprobantes");
  access.lockProfile(p.getProfileId());p=payments.lock(payment).orElseThrow(()->missing("Pago"));
  String doc=Objects.toString(tax.get("document_number"),"");
  String expected=type==BillingDocumentType.ELECTRONIC_RECEIPT?"DNI":"RUC";
  if(!expected.equals(tax.get("document_type"))||!doc.matches(type==BillingDocumentType.ELECTRONIC_RECEIPT?"[0-9]{8}":"[0-9]{11}"))throw new IllegalArgumentException("Datos tributarios incompatibles con el tipo de comprobante");
  var existing=documents.byPayment(payment);
  if(existing.isPresent()){
   var d=existing.get();
   if(d.getDocumentType()!=type||!d.getDocumentNumber().equals(number)||!d.getSeries().equals(series)||!d.getLegalName().equals(legalName)||!d.getTaxData().equals(tax))throw conflict("El pago ya tiene un comprobante diferente");
   return d;
  }
  return documents.saveAndFlush(new BillingDocument(p,type,number,series,legalName,tax,clock.instant()));
 }
 @Transactional
 public BillingDocument voidDocument(SpotgoUserPrincipal actor,UUID id){
  var d=documents.findById(id).orElseThrow(()->missing("Comprobante"));access.payment(actor,d.getPayment());
  if(actor.getRole()!=UserRole.PARKING_ADMIN&&actor.getRole()!=UserRole.SUPER_ADMIN)throw new AccessDeniedException("Solo emisor autorizado");
  payments.lock(d.getPaymentId());d.voidDocument();return documents.saveAndFlush(d);
 }
}
