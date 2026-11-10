package com.yachiqo.spotgo.profiles.interfaces.rest.resources;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
public record CreateVehicleResource(
    @Schema(description = "Placa opcional y unica. Se normaliza a mayusculas; vacia o ausente se guarda como NULL.", example = "DEM-104") String plate,
    @Schema(description = "Marca del vehiculo, obligatoria.", example = "Toyota") @NotBlank String make,
    @Schema(description = "Modelo del vehiculo, obligatorio.", example = "Yaris") @NotBlank String model,
    @Schema(description = "Color del vehiculo, obligatorio.", example = "Azul") @NotBlank String color) {}
