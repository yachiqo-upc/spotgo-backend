package com.yachiqo.spotgo.monitoring.interfaces.rest;
import com.yachiqo.spotgo.monitoring.application.*;
import com.yachiqo.spotgo.monitoring.interfaces.rest.resources.MonitoringResources.*;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import java.util.*;

@RestController @RequestMapping(value="/api/v1/occupancy-alerts",produces="application/json")
@PreAuthorize("hasAnyRole('PARKING_ADMIN','SUPER_ADMIN')")
@Tag(name="Occupancy alerts",description="Exactly one source is required: healthEventId or conflictId. evidenceRef identifies an evidence image; foreign keys identify the incident.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid UUID, timestamp, quality, or JSON.",content=@Content),
 @ApiResponse(responseCode="401",description="Invalid session.",content=@Content),
 @ApiResponse(responseCode="403",description="The role or tenant is outside the caller's access scope.",content=@Content),
 @ApiResponse(responseCode="404",description="The requested resource was not found.",content=@Content),
 @ApiResponse(responseCode="409",description="Invalid reference, duplicate resource, or incompatible state; occupancy is not fabricated.",content=@Content)
})
public class OccupancyAlertsController {
 private final MonitoringService service;
 public OccupancyAlertsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="List alerts in my scope", description = "Returns alerts associated with the tenant administered by the caller or otherwise visible to their role.")
 public List<AlertView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.alerts(actor,tenantId).stream().map(AlertView::of).toList();}
 @PostMapping("/{alertId}/acknowledge") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Acknowledge an alert",description="Sets acknowledgedAt and changes the alert status to ACKNOWLEDGED.")
 public AlertView acknowledge(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID alertId){return AlertView.of(service.updateAlert(actor,alertId,false));}
 @PostMapping("/{alertId}/close") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Close an alert for a resolved incident",description="Requires a RESOLVED conflict or a recovered health event. Sets closedAt; closing a notification does not imply that the sensor recovered.")
 public AlertView close(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID alertId){return AlertView.of(service.updateAlert(actor,alertId,true));}

 @PutMapping(value="/{alertId}/evidence",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
 @PreAuthorize("hasRole('PARKING_ADMIN')")
 @Operation(summary="Upload an evidence image",description="Upload a file using image as multipart/form-data; JSON and URLs are not accepted. Only real JPEG or PNG files up to 5 MB and 20 million pixels are accepted. Only the tenant administrator can attach evidence. The image is stored and the incident foreign keys are preserved.")
 @ApiResponse(responseCode="413",description="The file exceeds 5 MB.",content=@Content)
 @ApiResponse(responseCode="415",description="The content is not a JPEG or PNG image.",content=@Content)
 @ApiResponse(responseCode="503",description="Evidence storage is unavailable.",content=@Content)
 public AlertView evidence(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID alertId,
   @RequestPart("image") org.springframework.web.multipart.MultipartFile image){
  if(image.getSize()>EvidenceImageStorage.MAX_BYTES)throw new MonitoringException(HttpStatus.PAYLOAD_TOO_LARGE,"The image cannot exceed 5 MB.");
  try{return AlertView.of(service.uploadEvidence(actor,alertId,image.getBytes()));}
  catch(java.io.IOException e){throw new MonitoringException(HttpStatus.SERVICE_UNAVAILABLE,"The uploaded image could not be read.");}
 }
 @GetMapping(value="/{alertId}/evidence",produces={"image/jpeg","image/png"})
 @Operation(summary="View an evidence image",description="Returns the image bytes instead of conflict JSON. Requires a JWT from the tenant administrator or SUPER_ADMIN. Provide only the alert ID; the server locates the stored file.")
 public org.springframework.http.ResponseEntity<byte[]> photo(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID alertId){
  var image=service.evidence(actor,alertId);
  return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(image.contentType()))
   .contentLength(image.bytes().length).cacheControl(org.springframework.http.CacheControl.noStore())
   .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,org.springframework.http.ContentDisposition.inline().filename(image.fileName()).build().toString())
   .header("X-Content-Type-Options","nosniff").body(image.bytes());
 }
}