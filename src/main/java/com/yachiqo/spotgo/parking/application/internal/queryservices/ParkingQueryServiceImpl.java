package com.yachiqo.spotgo.parking.application.internal.queryservices;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandServiceImpl;
import com.yachiqo.spotgo.parking.domain.model.aggregates.*;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.time.*;
@Service @Transactional(readOnly=true)
public class ParkingQueryServiceImpl {
 private final TenantRepository tenants; private final ParkingZoneRepository zones;private final ParkingSpotRepository spots;
 private final FloorPlanRepository plans;private final DigitalParkingMapRepository maps;private final ReservationRepository reservations;
 private final ParkingSessionRepository sessions;private final GuestParkingSessionRepository guests;
 private final com.yachiqo.spotgo.parking.application.ParkingOccupancyGateway occupancy;
 private final ProfilesManagementService profiles;private final ParkingCommandServiceImpl commands;private final Clock clock;
 public ParkingQueryServiceImpl(TenantRepository tenants,ParkingZoneRepository zones,ParkingSpotRepository spots,FloorPlanRepository plans,
  DigitalParkingMapRepository maps,ReservationRepository reservations,ParkingSessionRepository sessions,GuestParkingSessionRepository guests,
  ProfilesManagementService profiles,ParkingCommandServiceImpl commands,Clock clock,com.yachiqo.spotgo.parking.application.ParkingOccupancyGateway occupancy) {
  this.tenants=tenants;this.zones=zones;this.spots=spots;this.plans=plans;this.maps=maps;this.reservations=reservations;
  this.sessions=sessions;this.guests=guests;this.profiles=profiles;this.commands=commands;this.clock=clock;this.occupancy=occupancy;
 }
 public List<Tenant> tenants(){return tenants.findAll();}
 public Tenant tenant(UUID id){return commands.tenant(id);}
 public List<ParkingZone> zones(UUID tenant){commands.tenant(tenant);return zones.byTenant(tenant);}
 public List<ParkingSpot> spots(UUID zone){commands.zone(zone);return spots.byZone(zone);}
 public List<FloorPlan> plans(SpotgoUserPrincipal actor,UUID zone){var z=commands.zone(zone);commands.admin(actor,z.getTenantId());return plans.byZone(zone);}
 public List<DigitalParkingMap> maps(SpotgoUserPrincipal actor,UUID plan){var p=commands.plan(plan);commands.admin(actor,p.getZone().getTenantId());return maps.byPlan(plan);}
 public List<Reservation> reservations(SpotgoUserPrincipal actor) {
  if(actor.getRole()==UserRole.DRIVER)return reservations.byProfile(profiles.driver(actor).getId());
  commands.admin(actor,actor.getTenantId());return reservations.byTenant(actor.getTenantId());
 }
 public Reservation reservation(SpotgoUserPrincipal actor,UUID id) {
  var r=reservations.findById(id).orElseThrow(()->ParkingCommandServiceImpl.missing("Reserva"));commands.reservationAccess(actor,r);return r;
 }
 public List<ParkingSession> sessions(SpotgoUserPrincipal actor) {
  if(actor.getRole()==UserRole.DRIVER)return sessions.byProfile(profiles.driver(actor).getId());
  commands.admin(actor,actor.getTenantId());return sessions.byTenant(actor.getTenantId());
 }
 public List<GuestParkingSession> guests(SpotgoUserPrincipal actor) {
  commands.admin(actor,actor.getTenantId());return guests.byTenant(actor.getTenantId());
 }
 public record Availability(UUID spotId,boolean bookable,String reason,String physicalOccupancy) {}
 public List<Availability> availability(UUID zone,Instant start,Instant end) {
  com.yachiqo.spotgo.parking.domain.model.valueobjects.ParkingValues.interval(start,end);
  return spots(zone).stream().map(s-> {
   boolean operational=s.getOperationalStatus()==com.yachiqo.spotgo.parking.domain.model.valueobjects.OperationalStatus.ACTIVE
    && s.getZone().getStatus()==com.yachiqo.spotgo.parking.domain.model.valueobjects.ZoneStatus.ACTIVE
    && s.getZone().getTenant().getStatus()==com.yachiqo.spotgo.parking.domain.model.valueobjects.TenantStatus.ACTIVE;
   boolean blocked=reservations.overlappingSpot(s.getId(),start,end).stream().anyMatch(r->r.blocks(clock.instant()))
    || !sessions.openSpot(s.getId()).isEmpty() || !guests.openSpot(s.getId()).isEmpty();
   String physical=occupancy.physicalOccupancy(s.getId());boolean immediate=!start.isAfter(clock.instant());
   boolean observedBlock=immediate&&!physical.equals("AVAILABLE");
   String reason=!operational?"NOT_OPERATIONAL":blocked?"RESERVATION_OR_OPEN_STAY":observedBlock?"PHYSICAL_"+physical:immediate?"AVAILABLE_NOW":"FUTURE_SLOT_NO_KNOWN_BLOCK";
   return new Availability(s.getId(),operational&&!blocked&&!observedBlock,reason,physical);
  }).toList();
 }
}
