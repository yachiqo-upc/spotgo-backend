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
@Tag(name="Occupancy alerts",description="Exactamente un origen: healthEventId o conflictId. evidenceRef identifica una imagen de evidencia; las FK identifican el incidente.")
@ApiResponses({
 @ApiResponse(responseCode="400",description="UUID, fecha, calidad o JSON invalidos",content=@Content),
 @ApiResponse(responseCode="401",description="Sesion no valida",content=@Content),
 @ApiResponse(responseCode="403",description="Rol o Tenant fuera de tu alcance",content=@Content),
 @ApiResponse(responseCode="404",description="Recurso inexistente",content=@Content),
 @ApiResponse(responseCode="409",description="Referencia, duplicado o estado incompatible; no se inventa ocupacion",content=@Content)
})
public class OccupancyAlertsController {
 private final MonitoringService service;
 public OccupancyAlertsController(MonitoringService service){this.service=service;}
 
 @GetMapping @Operation(summary="Listar avisos de mi alcance")
 public List<AlertView> list(@AuthenticationPrincipal SpotgoUserPrincipal actor,@RequestParam(required=false) UUID tenantId){return service.alerts(actor,tenantId).stream().map(AlertView::of).toList();}
 @PostMapping("/{alertId}/acknowledge") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Reconocer alerta",description="Captura acknowledgedAt y cambia a ACKNOWLEDGED.")
 public AlertView acknowledge(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID alertId){return AlertView.of(service.updateAlert(actor,alertId,false));}
 @PostMapping("/{alertId}/close") @PreAuthorize("hasRole('PARKING_ADMIN')") @Operation(summary="Cerrar alerta de incidente resuelto",description="Requiere conflicto RESOLVED o evento de salud recuperado. Captura closedAt; no afirma que el sensor se recupero por cerrar una notificacion.")
 public AlertView close(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID alertId){return AlertView.of(service.updateAlert(actor,alertId,true));}

 @PutMapping(value="/{alertId}/evidence",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
 @PreAuthorize("hasRole('PARKING_ADMIN')")
 @Operation(summary="Subir foto de evidencia",description="Selecciona un archivo en image. Recibe multipart/form-data, no JSON ni URLs. JPEG o PNG real, maximo 5 MB y 20 millones de pixeles. Solo el administrador del Tenant puede adjuntarla. Guarda la foto y conserva las FK del incidente.")
 @ApiResponse(responseCode="413",description="Archivo mayor a 5 MB",content=@Content)
 @ApiResponse(responseCode="415",description="El contenido no es JPEG o PNG",content=@Content)
 @ApiResponse(responseCode="503",description="Almacenamiento de evidencia no disponible",content=@Content)
 public AlertView evidence(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID alertId,
   @RequestPart("image") org.springframework.web.multipart.MultipartFile image){
  if(image.getSize()>EvidenceImageStorage.MAX_BYTES)throw new MonitoringException(HttpStatus.PAYLOAD_TOO_LARGE,"La imagen no puede superar 5 MB");
  try{return AlertView.of(service.uploadEvidence(actor,alertId,image.getBytes()));}
  catch(java.io.IOException e){throw new MonitoringException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo leer la foto enviada");}
 }
 @GetMapping(value="/{alertId}/evidence",produces={"image/jpeg","image/png"})
 @Operation(summary="Ver foto de evidencia",description="Devuelve los bytes de la foto, no el JSON del conflicto. Requiere JWT: administrador del Tenant o SUPER_ADMIN. Solo requiere el ID de la alerta; el servidor localiza el archivo guardado.")
 public org.springframework.http.ResponseEntity<byte[]> photo(@AuthenticationPrincipal SpotgoUserPrincipal actor,@PathVariable UUID alertId){
  var image=service.evidence(actor,alertId);
  return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(image.contentType()))
   .contentLength(image.bytes().length).cacheControl(org.springframework.http.CacheControl.noStore())
   .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,org.springframework.http.ContentDisposition.inline().filename(image.fileName()).build().toString())
   .header("X-Content-Type-Options","nosniff").body(image.bytes());
 }
}