package com.yachiqo.spotgo.parking.interfaces.rest;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandServiceImpl;
import com.yachiqo.spotgo.parking.application.internal.queryservices.ParkingQueryServiceImpl;
import com.yachiqo.spotgo.parking.interfaces.rest.resources.ParkingResources.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.Instant;

@RestController @RequestMapping(value="/api/v1/tenants",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Tenants",description="Each tenant represents a business and a single physical parking facility.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid data, UUID, JSON, date, or state transition.",content=@Content),
 @ApiResponse(responseCode="401",description="The access token or session is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="Insufficient role, another tenant, or another driver's resource.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, overlapping interval, expired hold, or incompatible state.",content=@Content)
})
public class TenantsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public TenantsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @Operation(summary="List tenants",description="Returns tenants available to the authenticated caller.")
 public List<TenantView> list(){return queries.tenants().stream().map(TenantView::of).toList();}
 @GetMapping("/{tenantId}") @Operation(summary="Get a tenant", description = "Returns the requested tenant when it is available within the caller's access scope.")
 public TenantView get(@PathVariable UUID tenantId){return TenantView.of(queries.tenant(tenantId));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SUPER_ADMIN')")
 @Operation(summary="Create a tenant",description="Available to SUPER_ADMIN only. Generates the UUID and timestamps. Does not create an account or assign an administrator automatically.")
 @ApiResponse(responseCode="201",description="Tenant created.")
 public TenantView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody TenantBody body) {
  return TenantView.of(commands.createTenant(actor,body.name(),body.address(),body.coordinates(),body.contactData(),body.rules()));
 }
 @PutMapping("/{tenantId}") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Update my tenant",description="Includes tariff rules in PEN. All required fields, including status, must be provided. Only the assigned tenant administrator can update the tenant.")
 public TenantView update(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID tenantId,@Valid @RequestBody TenantBody body) {
  if(body.status()==null)throw new IllegalArgumentException("status is required.");
  return TenantView.of(commands.updateTenant(actor,tenantId,body.name(),body.address(),body.coordinates(),body.contactData(),body.rules(),body.status()));
 }

}
