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

@RestController @RequestMapping(value="/api/v1/digital-parking-maps",produces="application/json")
@PreAuthorize("hasAnyRole('DRIVER','PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Digital parking maps",description="JSON elements rendered by the app from a floor plan. They do not create parking spots automatically.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid data, UUID, JSON, date, or state transition.",content=@Content),
 @ApiResponse(responseCode="401",description="The access token or session is invalid.",content=@Content),
 @ApiResponse(responseCode="403",description="Insufficient role, another tenant, or another driver's resource.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Duplicate resource, overlapping interval, expired hold, or incompatible state.",content=@Content)
})
public class DigitalParkingMapsController {
 private final ParkingCommandServiceImpl commands; private final ParkingQueryServiceImpl queries;
 public DigitalParkingMapsController(ParkingCommandServiceImpl commands,ParkingQueryServiceImpl queries){this.commands=commands;this.queries=queries;}

 @GetMapping @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="List maps for my floor plan", description = "Returns map versions for a floor plan managed by the authenticated parking administrator.")
 public List<MapView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam UUID floorPlanId){return queries.maps(actor,floorPlanId).stream().map(MapView::of).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Create a digital parking map version",description="Creates a DRAFT map version. SPOT elements require a spot_id from the floor plan's zone; other fields are used by the renderer.")
 @ApiResponse(responseCode="201",description="Map version created.")
 public MapView create(@AuthenticationPrincipal SpotgoUserPrincipal actor,@Valid @RequestBody MapBody body){return MapView.of(commands.createMap(actor,body.floorPlanId(),body.version(),body.elements()));}
 @PostMapping("/{mapId}/publish") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Publish a digital parking map",description="Archives the other versions of the floor plan so only one remains published.")
 public MapView publish(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID mapId){return MapView.of(commands.publishMap(actor,mapId,false));}
 @PostMapping("/{mapId}/archive") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Archive a digital parking map", description = "Archives the specified map version so it is no longer the published version for its floor plan.")
 public MapView archive(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID mapId){return MapView.of(commands.publishMap(actor,mapId,true));}

}
