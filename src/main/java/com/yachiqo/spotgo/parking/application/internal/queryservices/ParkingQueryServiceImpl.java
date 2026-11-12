package com.yachiqo.spotgo.parking.application.internal.queryservices;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandServiceImpl;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.valueobjects.DriverProfileType;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.DriverClassificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import java.util.*;
import java.time.*;
@Service @Transactional(readOnly=true)
public class ParkingQueryServiceImpl {
 private final TenantRepository tenants; private final ParkingZoneRepository zones;private final ParkingSpotRepository spots;
 private final FloorPlanRepository plans;private final DigitalParkingMapRepository maps;private final ReservationRepository reservations;
 private final ParkingSessionRepository sessions;private final GuestParkingSessionRepository guests;
 private final com.yachiqo.spotgo.parking.application.ParkingOccupancyGateway occupancy;
 private final ProfilesManagementService profiles;private final ParkingCommandServiceImpl commands;private final Clock clock;
  private final DriverClassificationRepository classifications;
  private final ReservationChangeRepository reservationChanges;
  public ParkingQueryServiceImpl(TenantRepository tenants,ParkingZoneRepository zones,ParkingSpotRepository spots,FloorPlanRepository plans,
   DigitalParkingMapRepository maps,ReservationRepository reservations,ParkingSessionRepository sessions,GuestParkingSessionRepository guests,
   ProfilesManagementService profiles,ParkingCommandServiceImpl commands,Clock clock,com.yachiqo.spotgo.parking.application.ParkingOccupancyGateway occupancy,
   DriverClassificationRepository classifications,ReservationChangeRepository reservationChanges) {
  this.tenants=tenants;this.zones=zones;this.spots=spots;this.plans=plans;this.maps=maps;this.reservations=reservations;
   this.sessions=sessions;this.guests=guests;this.profiles=profiles;this.commands=commands;this.clock=clock;this.occupancy=occupancy;this.classifications=classifications;this.reservationChanges=reservationChanges;
 }
 public List<Tenant> tenants(){return tenants.findAll();}
 public Tenant tenant(UUID id){return commands.tenant(id);}
 public List<ParkingZone> zones(SpotgoUserPrincipal actor,UUID tenant){
  commands.tenant(tenant);
  if(actor.getRole()==UserRole.PARKING_ADMIN) commands.admin(actor,tenant);
  if(actor.getRole()==UserRole.DRIVER) {
   var profile=profiles.driver(actor);var type=driverType(profile,tenant);
   return zones.byTenant(tenant).stream().filter(z->z.getAllowedProfileTypes().contains(type)).toList();
  }
  if(actor.getRole()!=UserRole.PARKING_ADMIN&&actor.getRole()!=UserRole.SUPER_ADMIN)
   throw new AccessDeniedException("The caller cannot list parking zones.");
  return zones.byTenant(tenant);
 }
 public List<ParkingSpot> spots(SpotgoUserPrincipal actor,UUID zoneId){
  var zone=commands.zone(zoneId);requireZoneAccess(actor,zone);return spots.byZone(zoneId);
 }
 public List<FloorPlan> plans(SpotgoUserPrincipal actor,UUID zone){var z=commands.zone(zone);commands.admin(actor,z.getTenantId());return plans.byZone(zone);}
 public List<DigitalParkingMap> maps(SpotgoUserPrincipal actor,UUID plan){var p=commands.plan(plan);commands.admin(actor,p.getZone().getTenantId());return maps.byPlan(plan);}
 public List<Reservation> reservations(SpotgoUserPrincipal actor) {
  if(actor.getRole()==UserRole.DRIVER)return reservations.byProfile(profiles.driver(actor).getId());
  commands.admin(actor,actor.getTenantId());return reservations.byTenant(actor.getTenantId());
 }
  public Reservation reservation(SpotgoUserPrincipal actor,UUID id) {
   var r=reservations.findById(id).orElseThrow(()->ParkingCommandServiceImpl.missing("Reservation"));commands.reservationAccess(actor,r);return r;
  }
  public List<ReservationChange> reservationChanges(SpotgoUserPrincipal actor,UUID id) {
   reservation(actor,id);return reservationChanges.byReservation(id);
  }
 public List<ParkingSession> sessions(SpotgoUserPrincipal actor) {
  if(actor.getRole()==UserRole.DRIVER)return sessions.byProfile(profiles.driver(actor).getId());
  commands.admin(actor,actor.getTenantId());return sessions.byTenant(actor.getTenantId());
 }
 public List<GuestParkingSession> guests(SpotgoUserPrincipal actor) {
  commands.admin(actor,actor.getTenantId());return guests.byTenant(actor.getTenantId());
 }
 public record Availability(UUID spotId,boolean bookable,String reason,String physicalOccupancy) {}
 public List<Availability> availability(SpotgoUserPrincipal actor,UUID zone,Instant start,Instant end) {
  com.yachiqo.spotgo.parking.domain.model.valueobjects.ParkingValues.interval(start,end);
  requireZoneAccess(actor,commands.zone(zone));
  return spots.byZone(zone).stream().map(s-> {
   boolean operational=s.getOperationalStatus()==com.yachiqo.spotgo.parking.domain.model.valueobjects.OperationalStatus.ACTIVE
    && s.getZone().getStatus()==com.yachiqo.spotgo.parking.domain.model.valueobjects.ZoneStatus.ACTIVE
    && s.getZone().getTenant().getStatus()==com.yachiqo.spotgo.parking.domain.model.valueobjects.TenantStatus.ACTIVE;
    boolean blocked=reservations.overlappingSpot(s.getId(),start,end).stream().anyMatch(r->r.blocks(clock.instant()))
     ||commands.pendingChangeOverlaps(s.getId(),start,end,null)
    || !sessions.openSpot(s.getId()).isEmpty() || !guests.openSpot(s.getId()).isEmpty();
   String physical=occupancy.physicalOccupancy(s.getId());boolean immediate=!start.isAfter(clock.instant());
   boolean observedBlock=immediate&&!physical.equals("AVAILABLE");
   String reason=!operational?"NOT_OPERATIONAL":blocked?"RESERVATION_OR_OPEN_STAY":observedBlock?"PHYSICAL_"+physical:immediate?"AVAILABLE_NOW":"FUTURE_SLOT_NO_KNOWN_BLOCK";
   return new Availability(s.getId(),operational&&!blocked&&!observedBlock,reason,physical);
  }).toList();
 }
 private DriverProfileType driverType(Profile profile,UUID tenantId) {
  return classifications.findByProfileIdAndTenantId(profile.getId(),tenantId)
   .map(com.yachiqo.spotgo.profiles.domain.model.aggregates.DriverClassification::getProfileType)
   .orElse(DriverProfileType.PRIVATE);
 }
 private void requireZoneAccess(SpotgoUserPrincipal actor,ParkingZone zone) {
  if(actor.getRole()==UserRole.PARKING_ADMIN) {
   commands.admin(actor,zone.getTenantId());return;
  }
  if(actor.getRole()==UserRole.SUPER_ADMIN)return;
  if(actor.getRole()!=UserRole.DRIVER) throw new AccessDeniedException("The caller cannot access this parking zone.");
  var profile=profiles.driver(actor);
  if(!zone.getAllowedProfileTypes().contains(driverType(profile,zone.getTenantId())))
   throw new AccessDeniedException("The driver's profile type is not allowed in this parking zone.");
 }
}
