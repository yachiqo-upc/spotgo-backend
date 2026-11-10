package com.yachiqo.spotgo.billing.application;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
@Service
public class BillingLifecycleService {
 private final BillingService billing;
 public BillingLifecycleService(BillingService billing){this.billing=billing;}
 @Scheduled(fixedDelayString="${app.billing.period-cleanup-ms:60000}")
 public void expirePeriods(){billing.expirePeriods();}
}