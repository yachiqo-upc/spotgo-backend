package com.yachiqo.spotgo.billing.infrastructure.providers;

import com.yachiqo.spotgo.billing.application.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies a no-money-movement provider for academic and demonstration deployments. */
@Configuration
public class SimulatedPaymentProviderConfiguration {
    @Bean
    @ConditionalOnMissingBean(PaymentProvider.class)
    public PaymentProvider paymentProvider() {
        return new SimulatedPaymentProvider();
    }
}
