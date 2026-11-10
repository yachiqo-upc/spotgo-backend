package com.yachiqo.spotgo.monitoring.application;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.time.*;
@Component
public class MonitoringPolicy {
 private final long freshSeconds;private final BigDecimal minimumQuality;
 public MonitoringPolicy(@Value("${app.monitoring.freshness-seconds:300}") long seconds,@Value("${app.monitoring.minimum-quality:80}") BigDecimal minimum){
  if(seconds<1||minimum.signum()<0||minimum.compareTo(BigDecimal.valueOf(100))>0)throw new IllegalArgumentException("Politica Monitoring invalida");
  freshSeconds=seconds;minimumQuality=minimum;
 }
 public boolean fresh(Instant occurred,Instant now){return !occurred.isAfter(now)&&!occurred.isBefore(now.minusSeconds(freshSeconds));}
 public boolean reliable(BigDecimal quality){return quality!=null&&quality.compareTo(minimumQuality)>=0;}
 public long freshnessSeconds(){return freshSeconds;}
}
