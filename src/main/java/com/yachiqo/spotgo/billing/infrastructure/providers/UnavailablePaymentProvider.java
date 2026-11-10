package com.yachiqo.spotgo.billing.infrastructure.providers;
import com.yachiqo.spotgo.billing.application.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import java.math.BigDecimal;
@Configuration
public class UnavailablePaymentProvider {
 @Bean @ConditionalOnMissingBean(PaymentProvider.class)
 public PaymentProvider paymentProvider(){
  return new PaymentProvider(){
   private BillingException unavailable(){return new BillingException(HttpStatus.SERVICE_UNAVAILABLE,"Proveedor de pagos no configurado; no se aprueban cobros ni reembolsos sin verificar");}
   public TokenDetails inspectToken(String ref){throw unavailable();}
   public PaymentResult charge(UUID id,int attempt,String token,BigDecimal amount,String currency){throw unavailable();}
   public PaymentResult reconcile(UUID id,int attempt,String ref){throw unavailable();}
   public RefundResult refund(UUID id,String ref,BigDecimal amount,String currency){throw unavailable();}
  };
 }
}
