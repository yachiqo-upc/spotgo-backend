package com.yachiqo.spotgo.parking.application.internal.commandservices;
import com.yachiqo.spotgo.parking.application.*;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.*;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.VehicleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

@Service @Transactional(readOnly=true)
public class ParkingCommandServiceImpl {
 private final TenantRepository tenants;
 private final ParkingZoneRepository zones;
 private final ParkingSpotRepository spots;
 private final FloorPlanRepository plans;
 private final DigitalParkingMapRepository maps;
 private final ReservationRepository reservations;
 private final ParkingSessionRepository sessions;
 private final GuestParkingSessionRepository guests;
 private final VehicleRepository vehicles;
 private final ProfilesManagementService profiles;
 private final ParkingPaymentGateway payments;
 private final Clock clock;
 public ParkingCommandServiceImpl(TenantRepository tenants,ParkingZoneRepository zones,ParkingSpotRepository spots,
  FloorPlanRepository plans,DigitalParkingMapRepository maps,ReservationRepository reservations,ParkingSessionRepository sessions,
  GuestParkingSessionRepository guests,VehicleRepository vehicles,ProfilesManagementService profiles,ParkingPaymentGateway payments,Clock clock) {
  this.tenants=tenants;this.zones=zones;this.spots=spots;this.plans=plans;this.maps=maps;this.reservations=reservations;
  this.sessions=sessions;this.guests=guests;this.vehicles=vehicles;this.profiles=profiles;this.payments=payments;this.clock=clock;
 }
 public static ParkingException missing(String resource) {return new ParkingException(HttpStatus.NOT_FOUND,resource+" inexistente");}
 public static ParkingException conflict(String message) {return new ParkingException(HttpStatus.CONFLICT,message);}
 public void admin(SpotgoUserPrincipal actor,UUID tenant) {
  if(actor==null || actor.getRole()!=UserRole.PARKING_ADMIN || !tenant.equals(actor.getTenantId()))
   throw new AccessDeniedException("No administras este tenant");
 }
 public Tenant tenant(UUID id) {return tenants.findById(id).orElseThrow(()->missing("Tenant"));}
 public ParkingZone zone(UUID id) {return zones.findById(id).orElseThrow(()->missing("Zona"));}
 public ParkingSpot spot(UUID id) {return spots.findById(id).orElseThrow(()->missing("Espacio"));}
 public FloorPlan plan(UUID id) {return plans.findById(id).orElseThrow(()->missing("Croquis"));}
 private void operational(ParkingSpot spot) {
  if(spot.getOperationalStatus()!=OperationalStatus.ACTIVE || spot.getZone().getStatus()!=ZoneStatus.ACTIVE ||
   spot.getZone().getTenant().getStatus()!=TenantStatus.ACTIVE) throw conflict("Tenant, zona y espacio deben estar habilitados");
 }
 private ParkingSpot lockSpot(UUID id) {return spots.lock(id).orElseThrow(()->missing("Espacio"));}
 private void usableInterval(Instant from,Instant to) {
  com.yachiqo.spotgo.parking.domain.model.valueobjects.ParkingValues.interval(from,to);
  if(Duration.between(from,to).compareTo(Duration.ofDays(365))>0) throw new IllegalArgumentException("El intervalo maximo es 365 dias");
 }
 @Transactional
 public Tenant createTenant(SpotgoUserPrincipal actor,String name,String address,Map<String,Object> coordinates,Map<String,Object> contact,Map<String,Object> rules) {
  if(actor==null || actor.getRole()!=UserRole.SUPER_ADMIN) throw new AccessDeniedException("Solo SUPER_ADMIN registra tenants");
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
  } catch(Exception e) {throw new IllegalArgumentException("coordinates requiere latitude [-90,90] y longitude [-180,180] numericos");}
 }
 @Transactional
 public ParkingZone createZone(SpotgoUserPrincipal actor,UUID tenant,String name,Map<String,Object> location,String color) {
  admin(actor,tenant);return zones.saveAndFlush(new ParkingZone(tenant(tenant),name,location,color));
 }
 @Transactional
 public ParkingZone updateZone(SpotgoUserPrincipal actor,UUID id,String name,Map<String,Object> location,String color,ZoneStatus status) {
  var z=zones.lock(id).orElseThrow(()->missing("Zona"));admin(actor,z.getTenantId());z.update(name,location,color,status);return zones.saveAndFlush(z);
 }
 @Transactional
 public ParkingSpot createSpot(SpotgoUserPrincipal actor,UUID zone,String code,String type,Map<String,Object> features) {
  var z=zone(zone);admin(actor,z.getTenantId());return spots.saveAndFlush(new ParkingSpot(z,code,type,features));
 }
 @Transactional
 public ParkingSpot updateSpot(SpotgoUserPrincipal actor,UUID id,String code,String type,Map<String,Object> features,OperationalStatus status) {
  var s=lockSpot(id);admin(actor,s.getTenantId());s.update(code,type,features,status);return spots.saveAndFlush(s);
 }
 @Transactional
 public FloorPlan createPlan(SpotgoUserPrincipal actor,UUID zone,int version,String reference) {
  var z=zones.lock(zone).orElseThrow(()->missing("Zona"));admin(actor,z.getTenantId());
  if(plans.byZone(zone).stream().anyMatch(p->p.getVersion()==version)) throw conflict("La version del croquis ya existe en la zona");
  return plans.saveAndFlush(new FloorPlan(z,version,reference));
 }
 @Transactional
 public FloorPlan publishPlan(SpotgoUserPrincipal actor,UUID id,boolean archive) {
  var p=plans.lock(id).orElseThrow(()->missing("Croquis"));admin(actor,p.getZone().getTenantId());
  if(archive) {p.archive();maps.byPlan(id).forEach(DigitalParkingMap::archive);} else p.publish(clock.instant());
  return plans.saveAndFlush(p);
 }
 @Transactional
 public DigitalParkingMap createMap(SpotgoUserPrincipal actor,UUID plan,int version,List<Map<String,Object>> elements) {
  var p=plans.lock(plan).orElseThrow(()->missing("Croquis"));admin(actor,p.getZone().getTenantId());
  if(maps.byPlan(plan).stream().anyMatch(m->m.getVersion()==version)) throw conflict("La version del mapa ya existe");
  // Validate spot references in the documented SPOT elements; positions remain renderer data.
  for(var e:elements) if("SPOT".equals(e.get("type"))) {
   if(e.get("spot_id")==null) throw new IllegalArgumentException("Un elemento SPOT requiere spot_id");
   var s=spot(UUID.fromString(e.get("spot_id").toString()));
   if(!s.getZoneId().equals(p.getZoneId())) throw conflict("El mapa referencia un espacio de otra zona");
  }
  return maps.saveAndFlush(new DigitalParkingMap(p,version,elements,clock.instant()));
 }
 @Transactional
 public DigitalParkingMap publishMap(SpotgoUserPrincipal actor,UUID id,boolean archive) {
  var selected=maps.findById(id).orElseThrow(()->missing("Mapa"));
  var p=plans.lock(selected.getFloorPlanId()).orElseThrow(()->missing("Croquis"));admin(actor,p.getZone().getTenantId());
  if(archive) selected.archive();
  else {
   if(p.getStatus()!=PublicationStatus.PUBLISHED) throw conflict("Publica primero el croquis");
   maps.byPlan(p.getId()).forEach(DigitalParkingMap::archive);selected.publish();
  }
  return maps.saveAndFlush(selected);
 }
 @Transactional
 public Reservation reserve(SpotgoUserPrincipal actor,UUID spotId,UUID vehicleId,Instant start,Instant end) {
  var profile=profiles.driver(actor);usableInterval(start,end);
  if(start.isBefore(clock.instant())) throw new IllegalArgumentException("No se puede reservar en el pasado");
  var vehicle=vehicles.lock(vehicleId).orElseThrow(()->missing("Vehiculo"));
  profiles.requireOwner(actor,vehicle);
  if(vehicle.getStatus()!=VehicleStatus.ACTIVE) throw conflict("Vehiculo inactivo");
  var spot=lockSpot(spotId);operational(spot);var now=clock.instant();
  if(reservations.overlappingSpot(spotId,start,end).stream().anyMatch(r->r.blocks(now))) throw conflict("El espacio ya tiene una reserva o bloqueo vigente");
  if(reservations.overlappingVehicle(vehicleId,start,end).stream().anyMatch(r->r.blocks(now))) throw conflict("El vehiculo ya tiene una reserva superpuesta");
  if(!sessions.openSpot(spotId).isEmpty() || !guests.openSpot(spotId).isEmpty()) throw conflict("El espacio tiene una estadia abierta");
  var rules=spot.getZone().getTenant().getRules();
  return reservations.saveAndFlush(new Reservation(spot,profile,vehicle,start,end,ParkingTariffs.price(rules,start,end),now,ParkingTariffs.lockMinutes(rules)));
 }
 public void reservationAccess(SpotgoUserPrincipal actor,Reservation r) {
  if(actor!=null && actor.getRole()==UserRole.DRIVER && actor.getAccountId().equals(r.getProfile().getAccountId())) return;
  admin(actor,r.getTenantId());
 }
 @Transactional
 public Reservation cancel(SpotgoUserPrincipal actor,UUID id) {
  var r=reservations.lock(id).orElseThrow(()->missing("Reserva"));reservationAccess(actor,r);
  r.expire(clock.instant());r.cancel();return reservations.saveAndFlush(r);
 }
 @Transactional
 public Reservation confirm(SpotgoUserPrincipal actor,UUID id,UUID payment) {
  var r=reservations.lock(id).orElseThrow(()->missing("Reserva"));reservationAccess(actor,r);
  if(!r.blocks(clock.instant()) || r.getStatus()!=ReservationStatus.PENDING_PAYMENT) throw conflict("Bloqueo vencido o reserva no pendiente");
  if(reservations.existsByPaymentId(payment)) throw conflict("El pago ya fue utilizado");
  payments.requireApproved(payment,r.getProfileId(),r.getAmount());r.confirm(payment,clock.instant());return reservations.saveAndFlush(r);
 }
 @Transactional
 public Reservation noShow(SpotgoUserPrincipal actor,UUID id) {
  var r=reservations.lock(id).orElseThrow(()->missing("Reserva"));admin(actor,r.getTenantId());
  if(clock.instant().isBefore(r.getEndAt())) throw conflict("Espera al fin del intervalo para registrar NO_SHOW");
  r.noShow();return reservations.saveAndFlush(r);
 }
 @Transactional
 public ParkingSession enter(SpotgoUserPrincipal actor,UUID reservation) {
  // Same lock order as reservation creation: vehicle -> spot -> reservation.
  var current=reservations.findById(reservation).orElseThrow(()->missing("Reserva"));admin(actor,current.getTenantId());
  vehicles.lock(current.getVehicleId()).orElseThrow(()->missing("Vehiculo"));
  var spot=lockSpot(current.getSpotId());var r=reservations.lock(reservation).orElseThrow(()->missing("Reserva"));operational(spot);
  var now=clock.instant();
  if(now.isBefore(r.getStartAt()) || !now.isBefore(r.getEndAt())) throw conflict("Entrada fuera del intervalo reservado");
  if(sessions.byReservation(reservation).isPresent() || !sessions.openVehicle(r.getVehicleId()).isEmpty() ||
    !sessions.openSpot(r.getSpotId()).isEmpty() || !guests.openSpot(r.getSpotId()).isEmpty()) throw conflict("Ya existe una estadia abierta o una sesion para esta reserva");
  r.start();return sessions.saveAndFlush(new ParkingSession(r,now));
 }
 @Transactional
 public ParkingSession exit(SpotgoUserPrincipal actor,UUID id) {
  var s=sessions.lock(id).orElseThrow(()->missing("Sesion"));admin(actor,s.getReservation().getTenantId());
  var r=reservations.lock(s.getReservationId()).orElseThrow(()->missing("Reserva"));
  var now=clock.instant();s.close(now);
  boolean exceeded=now.isAfter(r.getEndAt().plusSeconds(ParkingTariffs.toleranceMinutes(r.getTenant().getRules())*60));
  r.complete(exceeded);return sessions.saveAndFlush(s);
 }
 @Transactional
 public GuestParkingSession guestEnter(SpotgoUserPrincipal actor,UUID spotId,String plate,PhysicalPaymentMethod method) {
  var s=lockSpot(spotId);admin(actor,s.getTenantId());operational(s);var now=clock.instant();
  if(!guests.openSpot(spotId).isEmpty() || !sessions.openSpot(spotId).isEmpty() ||
   reservations.overlappingSpot(spotId,now,now.plusNanos(1)).stream().anyMatch(r->r.blocks(now))) throw conflict("Espacio no disponible ahora");
  return guests.saveAndFlush(new GuestParkingSession(s,plate,method,now));
 }
 @Transactional
 public GuestParkingSession guestExit(SpotgoUserPrincipal actor,UUID id,boolean paid) {
  var g=guests.lock(id).orElseThrow(()->missing("Sesion de invitado"));admin(actor,g.getTenantId());
  var now=clock.instant();g.close(now,ParkingTariffs.price(g.getTenant().getRules(),g.getEntryAt(),now),paid);return guests.saveAndFlush(g);
 }
 @Transactional
 public GuestParkingSession guestPaid(SpotgoUserPrincipal actor,UUID id) {
  var g=guests.lock(id).orElseThrow(()->missing("Sesion de invitado"));admin(actor,g.getTenantId());g.confirmPayment();return guests.saveAndFlush(g);
 }
}
