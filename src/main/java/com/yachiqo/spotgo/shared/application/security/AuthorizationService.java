package com.yachiqo.spotgo.shared.application.security;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.monitoring.domain.model.aggregates.Employee;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.TenantRepository;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Vehicle;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
@Service
public class AuthorizationService {
 private final TenantRepository tenants;
 public AuthorizationService(TenantRepository tenants){this.tenants=tenants;}
 public boolean isAdmin(SpotgoUserPrincipal principal){return principal!=null && principal.getRole()==UserRole.PARKING_ADMIN;}
 public boolean isClient(SpotgoUserPrincipal principal){return principal!=null && principal.getRole()==UserRole.DRIVER;}
 public String requireAdminParkingId(SpotgoUserPrincipal principal){
  if(!isAdmin(principal) || principal.getTenantId()==null || !tenants.existsById(principal.getTenantId()))
   throw new AccessDeniedException("El rol requiere un tenant existente asignado");
  return principal.getTenantId().toString();
 }
 public void requireParkingOwnership(SpotgoUserPrincipal principal,String tenantId){
  if(!requireAdminParkingId(principal).equals(tenantId))throw new AccessDeniedException("No administras ese tenant");
 }
 public void requireVehicleOwner(SpotgoUserPrincipal principal,Vehicle vehicle){
  if(!isClient(principal) || !vehicle.getProfile().getAccountId().equals(principal.getAccountId()))throw new AccessDeniedException("Vehiculo de otra cuenta");
 }
 public void requireEmployeeOwnership(SpotgoUserPrincipal principal,Employee employee){requireParkingOwnership(principal,employee.getParkingId());}
}