package com.yachiqo.spotgo.parking.application;
import java.math.*;
import java.time.*;
import java.util.*;
public final class ParkingTariffs {
 private ParkingTariffs() {}
 public static void validate(Map<String,Object> rules) {
  if(rules==null || !(rules.get("tariff_rules") instanceof Map<?,?> tariff)) throw new IllegalArgumentException("rules.tariff_rules is required.");
  positive(tariff.get("period_minutes"),"period_minutes");
  BigDecimal price=decimal(tariff.get("price_per_period"),"price_per_period");
  if(price.signum()<0 || price.scale()>2 || price.precision()>12) throw new IllegalArgumentException("Invalid price_per_period.");
  if(tariff.containsKey("rounding") && !"CEILING".equals(tariff.get("rounding"))) throw new IllegalArgumentException("Only CEILING rounding is supported.");
  positive(rules.getOrDefault("lock_duration_minutes",10),"lock_duration_minutes");
  BigDecimal tolerance=decimal(rules.getOrDefault("overstay_tolerance_minutes",5),"overstay_tolerance_minutes");
  if(tolerance.signum()<0 || tolerance.stripTrailingZeros().scale()>0) throw new IllegalArgumentException("Invalid grace period.");
  if(rules.containsKey("allocation_rules") && (!(rules.get("allocation_rules") instanceof Map<?,?> m) || !m.isEmpty()))
   throw new IllegalArgumentException("allocation_rules does not support criteria yet; use {}.");
  if(rules.containsKey("coexistence_rules") && (!(rules.get("coexistence_rules") instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank())))
   throw new IllegalArgumentException("coexistence_rules must be a list of strings.");
 }
 public static int lockMinutes(Map<String,Object> rules) { return positive(rules.getOrDefault("lock_duration_minutes",10),"lock_duration_minutes"); }
 public static long toleranceMinutes(Map<String,Object> rules) { return decimal(rules.getOrDefault("overstay_tolerance_minutes",5),"tolerancia").longValueExact(); }
 public static BigDecimal price(Map<String,Object> rules,Instant from,Instant to) {
  validate(rules);
  Map<?,?> tariff=(Map<?,?>)rules.get("tariff_rules");
  BigDecimal nanos=BigDecimal.valueOf(Duration.between(from,to).toNanos());
  BigDecimal unit=BigDecimal.valueOf(positive(tariff.get("period_minutes"),"period_minutes")).multiply(BigDecimal.valueOf(60000000000L));
  BigDecimal amount=nanos.divide(unit,0,RoundingMode.CEILING).multiply(decimal(tariff.get("price_per_period"),"price_per_period")).setScale(2,RoundingMode.HALF_UP);
  if(amount.precision()>12) throw new IllegalArgumentException("Amount is outside the DECIMAL(12,2) range.");
  return amount;
 }
 private static int positive(Object v,String name) {
  try {int n=decimal(v,name).intValueExact();if(n<=0 || n>525600) throw new IllegalArgumentException(name+" is out of range.");return n;}
  catch(ArithmeticException e){throw new IllegalArgumentException(name+" must be a positive integer.");}
 }
 private static BigDecimal decimal(Object v,String name) {
  try {return new BigDecimal(Objects.requireNonNull(v).toString());}
  catch(Exception e){throw new IllegalArgumentException(name+" must be numeric.");}
 }
}
