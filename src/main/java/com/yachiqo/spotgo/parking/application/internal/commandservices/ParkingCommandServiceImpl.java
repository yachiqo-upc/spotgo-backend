package com.yachiqo.spotgo.parking.application.internal.commandservices;
import com.yachiqo.spotgo.parking.application.*;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.*;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationChangeStatus;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.DriverClassificationRepository;
import com.yachiqo.spotgo.shared.application.events.SpotgoDomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

@Service @Transactional(readOnly=true)
public class ParkingCommandServiceImpl {
 public record SpotRegistration(ParkingSpot spot,ParkingPaymentGateway.SpotBillingResult billingAdjustment) {}
 private final TenantRepository tenants;
 private final ParkingZoneRepository zones;
 private final ParkingSpotRepository spots;
 private final FloorPlanRepository plans;
 private final DigitalParkingMapRepository maps;
  private final ReservationRepository reservations;
  private final ReservationChangeRepository reservationChanges;
 private final ParkingSessionRepository sessions;
 private final GuestParkingSessionRepository guests;
 private final VehicleRepository vehicles;
 private final ProfilesManagementService profiles;
 private final DriverClassificationRepository classifications;
 private final ParkingPaymentGateway payments;
 private final ParkingOccupancyGateway occupancy;
 private final SpotgoDomainEventPublisher domainEvents;
 private final Clock clock;
  public ParkingCommandServiceImpl(TenantRepository tenants,ParkingZoneRepository zones,ParkingSpotRepository spots,
   FloorPlanRepository plans,DigitalParkingMapRepository maps,ReservationRepository reservations,ReservationChangeRepository reservationChanges,ParkingSessionRepository sessions,
  GuestParkingSessionRepository guests,VehicleRepository vehicles,ProfilesManagementService profiles,DriverClassificationRepository classifications,ParkingPaymentGateway payments,Clock clock,ParkingOccupancyGateway occupancy,SpotgoDomainEventPublisher domainEvents) {
   this.tenants=tenants;this.zones=zones;this.spots=spots;this.plans=plans;this.maps=maps;this.reservations=reservations;this.reservationChanges=reservationChanges;
  this.sessions=sessions;this.guests=guests;this.vehicles=vehicles;this.profiles=profiles;this.classifications=classifications;this.payments=payments;this.clock=clock;this.occupancy=occupancy;this.domainEvents=domainEvents;
 }
 public static ParkingException missing(String resource) {return new ParkingException(HttpStatus.NOT_FOUND,resource+" not found");}
 public static ParkingException conflict(String message) {return new ParkingException(HttpStatus.CONFLICT,message);}
 public void admin(SpotgoUserPrincipal actor,UUID tenant) {
  if(actor==null || actor.getRole()!=UserRole.PARKING_ADMIN || !tenant.equals(actor.getTenantId()))
   throw new AccessDeniedException("You do not manage this tenant.");
 }
 public Tenant tenant(UUID id) {return tenants.findById(id).orElseThrow(()->missing("Tenant"));}
 public ParkingZone zone(UUID id) {return zones.findById(id).orElseThrow(()->missing("Zone"));}
 public ParkingSpot spot(UUID id) {return spots.findById(id).orElseThrow(()->missing("Parking spot"));}
 public FloorPlan plan(UUID id) {return plans.findById(id).orElseThrow(()->missing("Floor plan"));}
 private void operational(ParkingSpot spot) {
  if(spot.getOperationalStatus()!=OperationalStatus.ACTIVE || spot.getZone().getStatus()!=ZoneStatus.ACTIVE ||
   spot.getZone().getTenant().getStatus()!=TenantStatus.ACTIVE) throw conflict("The tenant, zone, and parking spot must be enabled.");
 }
 private ParkingSpot lockSpot(UUID id) {return spots.lock(id).orElseThrow(()->missing("Parking spot"));}
 private void usableInterval(Instant from,Instant to) {
  com.yachiqo.spotgo.parking.domain.model.valueobjects.ParkingValues.interval(from,to);
  if(Duration.between(from,to).compareTo(Duration.ofDays(365))>0) throw new IllegalArgumentException("The maximum interval is 365 days.");
 }
 @Transactional
 public Tenant createTenant(SpotgoUserPrincipal actor,String name,String address,Map<String,Object> coordinates,Map<String,Object> contact,Map<String,Object> rules) {
  if(actor==null || actor.getRole()!=UserRole.SUPER_ADMIN) throw new AccessDeniedException("Only SUPER_ADMIN can create tenants.");
  validateCoordinates(coordinates);ParkingTariffs.validate(rules);
  return tenants.saveAndFlush(new Tenant(name,address,coordinates,contact,rules));
 }
 @Transactional
 public Tenant updateTenant(SpotgoUserPrincipal actor,UUID id,String name,String address,Map<String,Object> coordinates,Map<String,Object> contact,Map<String,Object> rules,TenantStatus status) {
  admin(actor,id);var t=tenants.lock(id).orElseThrow(()->missing("Tenant"));
  validateCoordinates(coordinates);ParkingTariffs.validate(rules);t.update(name,address,coordinates,contact,rules,status);return tenants.saveAndFlush(t);
 }
 private void validateCoordinates(Map<String,Object> coordinates) {
  try {
   double lat=Double.parseDouble(coordinates.get("latitude").toString()),lon=Double.parseDouble(coordinates.get("longitude").toString());
   if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat < -90||lat>90||lon < -180||lon>180) throw new IllegalArgumentException();
  } catch(Exception e) {throw new IllegalArgumentException("coordinates must include numeric latitude in [-90, 90] and longitude in [-180, 180].");}
 }
 @Transactional
 public ParkingZone createZone(SpotgoUserPrincipal actor,UUID tenant,String name,Map<String,Object> location,String color) {
  return createZone(actor,tenant,name,location,color,null);
 }
 @Transactional
 public ParkingZone createZone(SpotgoUserPrincipal actor,UUID tenant,String name,Map<String,Object> location,String color,Set<DriverProfileType> allowedProfileTypes) {
  admin(actor,tenant);var zone=zones.saveAndFlush(new ParkingZone(tenant(tenant),name,location,color,allowedProfileTypes));
  domainEvents.publish("parking.zone.created","Parking Infrastructure","ParkingZone",zone.getId(),tenant,List.of(),Map.of("zone_id",zone.getId().toString()));
  return zone;
 }
 @Transactional
 public ParkingZone updateZone(SpotgoUserPrincipal actor,UUID id,String name,Map<String,Object> location,String color,ZoneStatus status) {
  return updateZone(actor,id,name,location,color,status,null);
 }
 @Transactional
 public ParkingZone updateZone(SpotgoUserPrincipal actor,UUID id,String name,Map<String,Object> location,String color,ZoneStatus status,Set<DriverProfileType> allowedProfileTypes) {
  var z=zones.lock(id).orElseThrow(()->missing("Zone"));admin(actor,z.getTenantId());z.update(name,location,color,status,allowedProfileTypes);return zones.saveAndFlush(z);
 }
 @Transactional
 public ParkingSpot createSpot(SpotgoUserPrincipal actor,UUID zoneId,String code,String type,Map<String,Object> features) {
  return createSpotWithBilling(actor,zoneId,code,type,features).spot();
 }
 @Transactional
 public SpotRegistration createSpotWithBilling(SpotgoUserPrincipal actor,UUID zoneId,String code,String type,Map<String,Object> features) {
  var z=zone(zoneId);admin(actor,z.getTenantId());
  payments.lockProfile(profiles.current(actor).getId());
  payments.prepareSpotAddition(actor,z.getTenantId());
  var spot=spots.saveAndFlush(new ParkingSpot(z,code,type,features));
  var billing=payments.billSpotAddition(actor,spot);
  domainEvents.publish("parking.spot.created","Parking Infrastructure","ParkingSpot",spot.getId(),spot.getTenantId(),List.of(),Map.of("spot_id",spot.getId().toString(),"zone_id",spot.getZoneId().toString()));
  return new SpotRegistration(spot,billing);
 }
 @Transactional
 public ParkingSpot updateSpot(SpotgoUserPrincipal actor,UUID id,String code,String type,Map<String,Object> features,OperationalStatus status) {
  var s=lockSpot(id);admin(actor,s.getTenantId());
  if(status==OperationalStatus.ACTIVE&&!payments.canActivateSpot(s.getTenantId(),id))
   throw conflict("The prorated charge for this parking spot has not been approved yet.");
  s.update(code,type,features,status);return spots.saveAndFlush(s);
 }
 @Transactional
 public FloorPlan createPlan(SpotgoUserPrincipal actor,UUID zone,int version,String reference) {
  var z=zones.lock(zone).orElseThrow(()->missing("Zone"));admin(actor,z.getTenantId());
  if(plans.byZone(zone).stream().anyMatch(p->p.getVersion()==version)) throw conflict("This floor plan version already exists in the zone.");
  return plans.saveAndFlush(new FloorPlan(z,version,reference));
 }
 @Transactional
 public FloorPlan publishPlan(SpotgoUserPrincipal actor,UUID id,boolean archive) {
  var p=plans.lock(id).orElseThrow(()->missing("Floor plan"));admin(actor,p.getZone().getTenantId());
  if(archive) {p.archive();maps.byPlan(id).forEach(DigitalParkingMap::archive);} else p.publish(clock.instant());
  return plans.saveAndFlush(p);
 }
 @Transactional
 public DigitalParkingMap createMap(SpotgoUserPrincipal actor,UUID plan,int version,List<Map<String,Object>> elements) {
  var p=plans.lock(plan).orElseThrow(()->missing("Floor plan"));admin(actor,p.getZone().getTenantId());
  if(maps.byPlan(plan).stream().anyMatch(m->m.getVersion()==version)) throw conflict("This map version already exists.");
  // Validate spot references in the documented SPOT elements; positions remain renderer data.
  for(var e:elements) if("SPOT".equals(e.get("type"))) {
   if(e.get("spot_id")==null) throw new IllegalArgumentException("A SPOT element requires spot_id.");
   var s=spot(UUID.fromString(e.get("spot_id").toString()));
   if(!s.getZoneId().equals(p.getZoneId())) throw conflict("The map references a parking spot from another zone.");
  }
  return maps.saveAndFlush(new DigitalParkingMap(p,version,elements,clock.instant()));
 }
 @Transactional
 public DigitalParkingMap publishMap(SpotgoUserPrincipal actor,UUID id,boolean archive) {
  var selected=maps.findById(id).orElseThrow(()->missing("Digital map"));
  var p=plans.lock(selected.getFloorPlanId()).orElseThrow(()->missing("Floor plan"));admin(actor,p.getZone().getTenantId());
  if(archive) selected.archive();
  else {
   if(p.getStatus()!=PublicationStatus.PUBLISHED) throw conflict("Publish the floor plan first.");
   maps.byPlan(p.getId()).forEach(DigitalParkingMap::archive);selected.publish();
  }
  return maps.saveAndFlush(selected);
 }
 @Transactional
 public Reservation reserve(SpotgoUserPrincipal actor,UUID spotId,UUID vehicleId,Instant start,Instant end) {
  var profile=profiles.driver(actor);payments.lockProfile(profile.getId());payments.requireNoDebt(profile.getId());usableInterval(start,end);
  if(start.isBefore(clock.instant())) throw new IllegalArgumentException("Reservations cannot be created in the past.");
  var vehicle=vehicles.lock(vehicleId).orElseThrow(()->missing("Vehicle"));
  profiles.requireOwner(actor,vehicle);
  if(vehicle.getStatus()!=VehicleStatus.ACTIVE) throw conflict("Vehicle is inactive.");
  var spot=lockSpot(spotId);operational(spot);var now=clock.instant();
  var driverType=classifications.findByProfileIdAndTenantId(profile.getId(),spot.getTenantId())
   .map(com.yachiqo.spotgo.profiles.domain.model.aggregates.DriverClassification::getProfileType)
   .orElse(DriverProfileType.PRIVATE);
  if(!spot.getZone().getAllowedProfileTypes().contains(driverType))
   throw new AccessDeniedException("The driver's profile type is not allowed in this parking zone.");
  if(!start.isAfter(now)&&!occupancy.physicalOccupancy(spotId).equals("AVAILABLE"))throw conflict("There is no reliable physical confirmation that the parking spot is free now.");
   if(reservations.overlappingSpot(spotId,start,end).stream().anyMatch(r->r.blocks(now))||pendingChangeOverlaps(spotId,start,end,null)) throw conflict("The parking spot already has an active reservation or hold.");
   if(reservations.overlappingVehicle(vehicleId,start,end).stream().anyMatch(r->r.blocks(now))||pendingChangeOverlapsVehicle(vehicleId,start,end,null)) throw conflict("The vehicle already has an overlapping reservation.");
  if(!sessions.openSpot(spotId).isEmpty() || !guests.openSpot(spotId).isEmpty()) throw conflict("The parking spot has an open session.");
  var rules=spot.getZone().getTenant().getRules();
  var reservation=reservations.saveAndFlush(new Reservation(spot,profile,vehicle,start,end,payments.reservationPrice(profile.getId(),ParkingTariffs.price(rules,start,end),now),now,ParkingTariffs.lockMinutes(rules)));
  domainEvents.publish("parking.reservation.created","Parking Infrastructure","Reservation",reservation.getId(),reservation.getTenantId(),List.of(profile.getAccountId()),Map.of("reservation_id",reservation.getId().toString(),"status",reservation.getStatus().name()));
  return reservation;
 }
 public boolean pendingChangeOverlaps(UUID spotId,Instant start,Instant end,UUID exceptReservation) {
  return reservationChanges.overlapping(spotId,start,end,ReservationChangeStatus.PENDING_PAYMENT).stream()
   .anyMatch(c->exceptReservation==null||!c.getReservationId().equals(exceptReservation));
 }
 public boolean pendingChangeOverlapsVehicle(UUID vehicleId,Instant start,Instant end,UUID exceptReservation) {
  return reservationChanges.overlappingVehicle(vehicleId,start,end,ReservationChangeStatus.PENDING_PAYMENT).stream()
   .anyMatch(c->exceptReservation==null||!c.getReservationId().equals(exceptReservation));
 }
 @Transactional
 public ParkingPaymentGateway.ReservationChangeResult reschedule(SpotgoUserPrincipal actor,UUID id,Instant start,Instant end) {
  var current=reservations.findById(id).orElseThrow(()->missing("Reservation"));reservationAccess(actor,current);
  if(actor==null||actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("Only the driver can reschedule a reservation.");
  payments.lockProfile(current.getProfileId());vehicles.lock(current.getVehicleId()).orElseThrow(()->missing("Vehicle"));
  var spot=lockSpot(current.getSpotId());var r=reservations.lock(id).orElseThrow(()->missing("Reservation"));reservationAccess(actor,r);
  var now=clock.instant();
  if(r.getStatus()!=ReservationStatus.RESERVED||r.getParentReservationId()!=null)throw conflict("Only a confirmed primary reservation can be rescheduled.");
  if(now.isAfter(r.getStartAt().minusSeconds(300)))throw conflict("Reservations can be rescheduled only until five minutes before they start.");
  usableInterval(start,end);if(start.isBefore(now))throw new IllegalArgumentException("A reservation cannot be moved into the past.");
  operational(spot);
  if(reservations.overlappingSpot(r.getSpotId(),start,end).stream().anyMatch(other->!other.getId().equals(id)&&other.blocks(now))||
     reservations.overlappingVehicle(r.getVehicleId(),start,end).stream().anyMatch(other->!other.getId().equals(id)&&other.blocks(now))||
     pendingChangeOverlaps(r.getSpotId(),start,end,id)||pendingChangeOverlapsVehicle(r.getVehicleId(),start,end,id))throw conflict("The requested interval is no longer available.");
  var base=ParkingTariffs.price(r.getTenant().getRules(),start,end);
  var price=payments.reservationPrice(r.getProfileId(),base,now);
  var result=payments.beginReschedule(actor,r,start,end,price);
  domainEvents.publish("parking.reservation.reschedule_requested","Parking Infrastructure","Reservation",r.getId(),r.getTenantId(),
   List.of(r.getProfile().getAccountId()),Map.of("reservation_id",r.getId().toString(),"change_id",result.change().getId().toString(),"status",result.change().getStatus().name()));
  return result;
 }
 @Transactional
 public Reservation extend(SpotgoUserPrincipal actor,UUID id,Instant end) {
  return extend(actor,id,end,null);
 }
 @Transactional
 public Reservation extend(SpotgoUserPrincipal actor,UUID id,Instant end,UUID requestedSpotId) {
  var current=reservations.findById(id).orElseThrow(()->missing("Reservation"));reservationAccess(actor,current);
  if(actor==null||actor.getRole()!=UserRole.DRIVER)throw new AccessDeniedException("Only the driver can extend a reservation.");
  var root=current;
  while(root.getParentReservation()!=null)root=root.getParentReservation();
  payments.lockProfile(root.getProfileId());vehicles.lock(root.getVehicleId()).orElseThrow(()->missing("Vehicle"));
  var originalSpot=lockSpot(root.getSpotId());root=reservations.lock(root.getId()).orElseThrow(()->missing("Reservation"));
  reservationAccess(actor,root);var now=clock.instant();
  if(root.getStatus()!=ReservationStatus.ACTIVE||!now.isBefore(root.getEndAt()))throw conflict("An extension requires an active reservation and payment before its original end time.");
  if(sessions.byReservation(root.getId()).filter(s->s.getExitAt()==null).isEmpty())throw conflict("The reservation has no active parking session.");
  var spot=requestedSpotId==null||requestedSpotId.equals(originalSpot.getId())?originalSpot:lockSpot(requestedSpotId);
  if(!spot.getTenantId().equals(root.getTenantId()))throw conflict("An extension must use a parking spot in the same facility.");
  operational(spot);
  var driverType=classifications.findByProfileIdAndTenantId(root.getProfileId(),root.getTenantId())
   .map(com.yachiqo.spotgo.profiles.domain.model.aggregates.DriverClassification::getProfileType)
   .orElse(DriverProfileType.PRIVATE);
  if(!spot.getZone().getAllowedProfileTypes().contains(driverType))
   throw new AccessDeniedException("The driver's profile type is not allowed in this parking zone.");
  var tail=root;
  while(true){
   var next=reservations.extensions(tail.getId()).stream()
    .filter(candidate->candidate.getStatus()==ReservationStatus.PENDING_PAYMENT||candidate.getStatus()==ReservationStatus.RESERVED)
    .max(Comparator.comparing(Reservation::getEndAt)).orElse(null);
   if(next==null)break;
   if(next.getStatus()==ReservationStatus.PENDING_PAYMENT)throw conflict("Pay for the existing extension before requesting another one.");
   tail=next;
  }
  usableInterval(tail.getEndAt(),end);
  if(!end.isAfter(tail.getEndAt()))throw new IllegalArgumentException("The extension must end after the current reservation segment.");
  if(reservations.overlappingSpot(spot.getId(),tail.getEndAt(),end).stream().anyMatch(other->other.blocks(now))||
     pendingChangeOverlaps(spot.getId(),tail.getEndAt(),end,null))throw conflict("The requested extension interval is not available.");
  if(!spot.getId().equals(originalSpot.getId())&&(!sessions.openSpot(spot.getId()).isEmpty()||!guests.openSpot(spot.getId()).isEmpty()))
   throw conflict("The selected parking spot has an active session.");
  if(reservations.overlappingVehicle(root.getVehicleId(),tail.getEndAt(),end).stream().anyMatch(other->other.blocks(now)))
   throw conflict("The vehicle has an overlapping reservation.");
  if(pendingChangeOverlapsVehicle(root.getVehicleId(),tail.getEndAt(),end,null))throw conflict("The vehicle has a pending rescheduling change in that interval.");
  var amount=payments.reservationPrice(root.getProfileId(),ParkingTariffs.price(root.getTenant().getRules(),tail.getEndAt(),end),now);
  var extension=reservations.saveAndFlush(new Reservation(spot,root.getProfile(),root.getVehicle(),tail,tail.getEndAt(),end,
   amount,now,ParkingTariffs.lockMinutes(root.getTenant().getRules())));
  domainEvents.publish("parking.reservation.extension_requested","Parking Infrastructure","Reservation",extension.getId(),extension.getTenantId(),
   List.of(root.getProfile().getAccountId()),Map.of("reservation_id",extension.getId().toString(),"parent_reservation_id",tail.getId().toString()));
  return extension;
 }
 public void reservationAccess(SpotgoUserPrincipal actor,Reservation r) {
  if(actor!=null && actor.getRole()==UserRole.DRIVER && actor.getAccountId().equals(r.getProfile().getAccountId())) return;
  admin(actor,r.getTenantId());
 }
 @Transactional
 public Reservation cancel(SpotgoUserPrincipal actor,UUID id) {
  var current=reservations.findById(id).orElseThrow(()->missing("Reservation"));reservationAccess(actor,current);payments.lockProfile(current.getProfileId());
  var r=reservations.lock(id).orElseThrow(()->missing("Reservation"));reservationAccess(actor,r);
  r.expire(clock.instant());if(r.getStatus()!=ReservationStatus.CANCELLED)r.cancel();payments.cancelled(r);var saved=reservations.saveAndFlush(r);
  domainEvents.publish("parking.reservation.cancelled","Parking Infrastructure","Reservation",r.getId(),r.getTenantId(),List.of(r.getProfile().getAccountId()),Map.of("reservation_id",r.getId().toString()));
  return saved;
 }
 @Transactional
 public Reservation confirm(SpotgoUserPrincipal actor,UUID id,UUID payment) {
  var current=reservations.findById(id).orElseThrow(()->missing("Reservation"));reservationAccess(actor,current);payments.lockProfile(current.getProfileId());
  var r=reservations.lock(id).orElseThrow(()->missing("Reservation"));reservationAccess(actor,r);
  if(r.getStatus()==ReservationStatus.RESERVED && payment.equals(r.getPaymentId()))return r;
  if(!r.blocks(clock.instant()) || r.getStatus()!=ReservationStatus.PENDING_PAYMENT) throw conflict("The hold has expired or the reservation is not pending.");
  if(!payment.equals(r.getPaymentId())) throw conflict("This payment is not the operation associated with this reservation.");
  payments.requireApproved(payment,r.getProfileId(),r.getAmount());r.confirm(payment,clock.instant());var saved=reservations.saveAndFlush(r);
  domainEvents.publish("parking.reservation.confirmed","Parking Infrastructure","Reservation",r.getId(),r.getTenantId(),List.of(r.getProfile().getAccountId()),Map.of("reservation_id",r.getId().toString()));
  return saved;
 }
 @Transactional
 public Reservation noShow(SpotgoUserPrincipal actor,UUID id) {
  var r=reservations.lock(id).orElseThrow(()->missing("Reservation"));admin(actor,r.getTenantId());
  if(clock.instant().isBefore(r.getEndAt())) throw conflict("Wait until the reservation interval ends before recording NO_SHOW.");
  r.noShow();var saved=reservations.saveAndFlush(r);
  domainEvents.publish("parking.reservation.no_show","Parking Infrastructure","Reservation",r.getId(),r.getTenantId(),List.of(r.getProfile().getAccountId()),Map.of("reservation_id",r.getId().toString()));
  return saved;
 }
 @Transactional
 public ParkingSession enter(SpotgoUserPrincipal actor,UUID reservation) {
  // Same lock order as reservation creation: vehicle -> spot -> reservation.
  var current=reservations.findById(reservation).orElseThrow(()->missing("Reservation"));admin(actor,current.getTenantId());
  payments.lockProfile(current.getProfileId());
  vehicles.lock(current.getVehicleId()).orElseThrow(()->missing("Vehicle"));
  var spot=lockSpot(current.getSpotId());var r=reservations.lock(reservation).orElseThrow(()->missing("Reservation"));operational(spot);
  var now=clock.instant();
  if(now.isBefore(r.getStartAt()) || !now.isBefore(r.getEndAt())) throw conflict("Entry is outside the reserved interval.");
  if(sessions.byReservation(reservation).isPresent() || !sessions.openVehicle(r.getVehicleId()).isEmpty() ||
    !sessions.openSpot(r.getSpotId()).isEmpty() || !guests.openSpot(r.getSpotId()).isEmpty()) throw conflict("An open parking session already exists for this reservation.");
  r.start();var session=sessions.saveAndFlush(new ParkingSession(r,now));
  domainEvents.publish("parking.session.started","Parking Infrastructure","ParkingSession",session.getId(),r.getTenantId(),List.of(r.getProfile().getAccountId()),Map.of("session_id",session.getId().toString(),"reservation_id",r.getId().toString()));
  return session;
 }
 @Transactional
 public ParkingSession exit(SpotgoUserPrincipal actor,UUID id) {
  var current=sessions.findById(id).orElseThrow(()->missing("Parking session"));admin(actor,current.getReservation().getTenantId());payments.lockProfile(current.getProfileId());
   var s=sessions.lock(id).orElseThrow(()->missing("Parking session"));admin(actor,s.getReservation().getTenantId());
   var r=reservations.lock(s.getReservationId()).orElseThrow(()->missing("Reservation"));
   var now=clock.instant();s.close(now);
   var tail=r;
   while(true){
    var next=reservations.extensions(tail.getId()).stream().filter(segment->segment.getStatus()==ReservationStatus.RESERVED)
     .max(Comparator.comparing(Reservation::getEndAt)).orElse(null);
    if(next==null)break;tail=next;
   }
   boolean exceeded=now.isAfter(tail.getEndAt().plusSeconds(ParkingTariffs.toleranceMinutes(tail.getTenant().getRules())*60));
   r.complete(exceeded);
   var segment=tail;
   while(segment!=r){segment.complete(exceeded);segment=segment.getParentReservation();}
   sessions.saveAndFlush(s);payments.completed(tail,s);
  domainEvents.publish("parking.session.completed","Parking Infrastructure","ParkingSession",s.getId(),r.getTenantId(),List.of(r.getProfile().getAccountId()),Map.of("session_id",s.getId().toString(),"reservation_id",r.getId().toString(),"overstay",Boolean.toString(exceeded)));
  return s;
 }
 @Transactional
 public GuestParkingSession guestEnter(SpotgoUserPrincipal actor,UUID spotId,String plate,PhysicalPaymentMethod method) {
  var s=lockSpot(spotId);admin(actor,s.getTenantId());operational(s);var now=clock.instant();
  if(!guests.openSpot(spotId).isEmpty() || !sessions.openSpot(spotId).isEmpty() ||
   reservations.overlappingSpot(spotId,now,now.plusNanos(1)).stream().anyMatch(r->r.blocks(now))) throw conflict("The parking spot is not available now.");
  return guests.saveAndFlush(new GuestParkingSession(s,plate,method,now));
 }
 @Transactional
 public GuestParkingSession guestExit(SpotgoUserPrincipal actor,UUID id,boolean paid) {
  var g=guests.lock(id).orElseThrow(()->missing("Guest parking session"));admin(actor,g.getTenantId());
  var now=clock.instant();g.close(now,ParkingTariffs.price(g.getTenant().getRules(),g.getEntryAt(),now),paid);return guests.saveAndFlush(g);
 }
 @Transactional
 public GuestParkingSession guestPaid(SpotgoUserPrincipal actor,UUID id) {
  var g=guests.lock(id).orElseThrow(()->missing("Guest parking session"));admin(actor,g.getTenantId());g.confirmPayment();return guests.saveAndFlush(g);
 }
}
