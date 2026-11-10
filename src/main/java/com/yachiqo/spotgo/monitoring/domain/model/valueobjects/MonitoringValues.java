package com.yachiqo.spotgo.monitoring.domain.model.valueobjects;
import java.time.Instant;
import java.util.Objects;
public final class MonitoringValues {
 private MonitoringValues(){}
 public static String text(String value){if(value==null||value.isBlank()||value.length()>255)throw new IllegalArgumentException("Text is required and must not exceed 255 characters.");return value.trim();}
 public static Instant instant(Instant value){return Objects.requireNonNull(value,"Timestamp is required.");}
}
