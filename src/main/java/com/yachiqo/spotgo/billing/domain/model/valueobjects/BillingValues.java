package com.yachiqo.spotgo.billing.domain.model.valueobjects;
import java.math.*;
import java.util.*;
public final class BillingValues {
 private BillingValues() {}
 public static String text(String value) {
  if(value==null||value.isBlank()||value.length()>255)throw new IllegalArgumentException("Text is required and must not exceed 255 characters.");
  return value.trim();
 }
 public static BigDecimal amount(BigDecimal value) {
  if(value==null||value.signum()<0||value.scale()>2||value.precision()>12)throw new IllegalArgumentException("Amount is invalid for DECIMAL(12,2).");
  return value.setScale(2);
 }
 public static BigDecimal positive(BigDecimal value) {
  value=amount(value);if(value.signum()==0)throw new IllegalArgumentException("The amount must be positive.");return value;
 }
}
