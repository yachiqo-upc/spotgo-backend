package com.yachiqo.spotgo.billing.application;
import com.yachiqo.spotgo.billing.domain.model.aggregates.*;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.math.BigDecimal;
@Service @Transactional(readOnly=true)
public class BillingQueryService {
 private final BillingAccess access;
 private final PaymentTokenRepository tokens;private final DigitalPaymentRepository payments;private final PaymentAttemptRepository attempts;
 private final SubscriptionRepository subscriptions;private final AdditionalChargeRepository charges;private final OutstandingBalanceRepository balances;
 private final RefundRepository refunds;private final BillingDocumentRepository documents;
 public BillingQueryService(BillingAccess access,PaymentTokenRepository tokens,DigitalPaymentRepository payments,PaymentAttemptRepository attempts,
  SubscriptionRepository subscriptions,AdditionalChargeRepository charges,OutstandingBalanceRepository balances,RefundRepository refunds,BillingDocumentRepository documents){
  this.access=access;this.tokens=tokens;this.payments=payments;this.attempts=attempts;this.subscriptions=subscriptions;this.charges=charges;this.balances=balances;this.refunds=refunds;this.documents=documents;
 }
 public List<PaymentToken> tokens(SpotgoUserPrincipal actor){return tokens.byProfile(access.profile(actor).getId());}
 public DigitalPayment payment(SpotgoUserPrincipal actor,UUID id){
  var p=payments.findById(id).orElseThrow(()->BillingService.missing("Pago"));access.payment(actor,p);return p;
 }
 public List<DigitalPayment> payments(SpotgoUserPrincipal actor){
  if(actor.getRole()==UserRole.SUPER_ADMIN)return payments.findAll();
  return payments.byProfile(access.profile(actor).getId());
 }
 public List<PaymentAttempt> attempts(SpotgoUserPrincipal actor,UUID id){payment(actor,id);return attempts.byPayment(id);}
 public List<Subscription> subscriptions(SpotgoUserPrincipal actor){
  if(actor.getRole()==UserRole.SUPER_ADMIN)return subscriptions.findAll();
  if(actor.getRole()==UserRole.PARKING_ADMIN){access.tenant(actor,actor.getTenantId());return subscriptions.byTenant(actor.getTenantId());}
  return subscriptions.byProfile(access.profile(actor).getId());
 }
 public Subscription subscription(SpotgoUserPrincipal actor,UUID id){
  var s=subscriptions.findById(id).orElseThrow(()->BillingService.missing("Suscripcion"));
  if(actor.getRole()!=UserRole.SUPER_ADMIN)access.subscription(actor,s);return s;
 }
 public List<AdditionalCharge> charges(SpotgoUserPrincipal actor){
  if(actor.getRole()==UserRole.SUPER_ADMIN)return charges.findAll();
  if(actor.getRole()==UserRole.PARKING_ADMIN){access.tenant(actor,actor.getTenantId());return charges.byTenant(actor.getTenantId());}
  return charges.byProfile(access.profile(actor).getId());
 }
 public List<OutstandingBalance> balances(SpotgoUserPrincipal actor){
  if(actor.getRole()==UserRole.SUPER_ADMIN)return balances.findAll();
  if(actor.getRole()==UserRole.PARKING_ADMIN){access.tenant(actor,actor.getTenantId());return balances.byTenant(actor.getTenantId());}
  return balances.byProfile(access.profile(actor).getId());
 }
 public BigDecimal outstandingTotal(SpotgoUserPrincipal actor){
  return balances(actor).stream().filter(b->b.getStatus()==com.yachiqo.spotgo.billing.domain.model.valueobjects.OutstandingBalanceStatus.OPEN).map(OutstandingBalance::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
 }
 public List<Refund> refunds(SpotgoUserPrincipal actor,UUID payment){payment(actor,payment);return refunds.byPayment(payment);}
 public Optional<BillingDocument> document(SpotgoUserPrincipal actor,UUID payment){payment(actor,payment);return documents.byPayment(payment);}
}
