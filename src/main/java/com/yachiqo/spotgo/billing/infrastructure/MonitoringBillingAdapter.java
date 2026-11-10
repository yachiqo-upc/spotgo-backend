package com.yachiqo.spotgo.billing.infrastructure;
import com.yachiqo.spotgo.billing.application.BillingService;
import com.yachiqo.spotgo.monitoring.application.MonitoringSubscriptionGateway;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class MonitoringBillingAdapter implements MonitoringSubscriptionGateway {
 private final BillingService billing;
 public MonitoringBillingAdapter(BillingService billing){this.billing=billing;}
 public int sensorLimit(UUID tenant){return billing.sensorLimit(tenant);}
 public boolean spotLicensed(UUID tenant,UUID spot){return billing.spotLicensed(tenant,spot);}
}
