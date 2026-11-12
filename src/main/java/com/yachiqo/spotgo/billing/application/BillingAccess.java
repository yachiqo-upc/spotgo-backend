package com.yachiqo.spotgo.billing.application;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.ProfileRepository;
import com.yachiqo.spotgo.billing.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationChangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import java.util.UUID;
@Service
public class BillingAccess {
 private final ProfilesManagementService profiles;private final ProfileRepository repository;private final ReservationRepository reservations;private final ReservationChangeRepository reservationChanges;
 private final com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.AdditionalChargeRepository charges;
 private final com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.SubscriptionRepository subscriptions;
 public BillingAccess(ProfilesManagementService profiles,ProfileRepository repository,ReservationRepository reservations,ReservationChangeRepository reservationChanges,com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.AdditionalChargeRepository charges,com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.SubscriptionRepository subscriptions){this.profiles=profiles;this.repository=repository;this.reservations=reservations;this.reservationChanges=reservationChanges;this.charges=charges;this.subscriptions=subscriptions;}
 public Profile profile(SpotgoUserPrincipal actor){
  if(actor==null || (actor.getRole()!=UserRole.DRIVER&&actor.getRole()!=UserRole.PARKING_ADMIN))throw new AccessDeniedException("This role requires a driver or administrator profile.");
  return profiles.current(actor);
 }
 public Profile lockProfile(UUID id){return repository.lock(id).orElseThrow(()->new BillingException(HttpStatus.NOT_FOUND,"Profile not found."));}
 public void owner(SpotgoUserPrincipal actor,Profile profile){
  if(actor==null||!profile.getAccountId().equals(actor.getAccountId()))throw new AccessDeniedException("Resource belongs to another profile.");
 }
 public void tenant(SpotgoUserPrincipal actor,UUID tenant){
  if(actor==null||actor.getRole()!=UserRole.PARKING_ADMIN||tenant==null||!tenant.equals(actor.getTenantId()))throw new AccessDeniedException("You do not manage this tenant.");
 }
 public void charge(SpotgoUserPrincipal actor,AdditionalCharge charge){
  if(actor!=null&&actor.getRole()==UserRole.DRIVER){owner(actor,charge.getReservation().getProfile());return;}
  tenant(actor,charge.getReservation().getTenantId());
 }
 public void subscription(SpotgoUserPrincipal actor,Subscription subscription){
  if(subscription.getProfile()!=null)owner(actor,subscription.getProfile());else tenant(actor,subscription.getTenantId());
 }
 public void payment(SpotgoUserPrincipal actor,DigitalPayment payment){
  if(actor!=null&&actor.getRole()==UserRole.SUPER_ADMIN)return;
  if(actor!=null&&payment.getProfile().getAccountId().equals(actor.getAccountId()))return;
   var reservation=reservations.byPayment(payment.getId());
   if(reservation.isPresent()){tenant(actor,reservation.get().getTenantId());return;}
   var change=reservationChanges.byPayment(payment.getId());if(change.isPresent()){tenant(actor,change.get().getReservation().getTenantId());return;}
  var charge=charges.byPayment(payment.getId());if(charge.isPresent()){tenant(actor,charge.get().getReservation().getTenantId());return;}
  var subscription=subscriptions.byPayment(payment.getId());if(subscription.isPresent()&&subscription.get().getTenantId()!=null){tenant(actor,subscription.get().getTenantId());return;}
  throw new AccessDeniedException("Payment is outside your access scope.");
 }
}
