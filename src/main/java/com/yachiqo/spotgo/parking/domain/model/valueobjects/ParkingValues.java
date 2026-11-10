package com.yachiqo.spotgo.parking.domain.model.valueobjects;
import java.time.Instant;
import java.util.*;
public final class ParkingValues {
 private ParkingValues() {}
 public static String text(String value) {
  if (value == null || value.isBlank()) throw new IllegalArgumentException("El texto es obligatorio");
  if (value.length() > 255) throw new IllegalArgumentException("Texto demasiado largo");
  return value.trim();
 }
 public static Map<String,Object> object(Map<String,Object> value) {
  return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
 }
 public static void interval(Instant from, Instant to) {
  if (from == null || to == null || !to.isAfter(from)) throw new IllegalArgumentException("El fin debe ser posterior al inicio");
 }
}
