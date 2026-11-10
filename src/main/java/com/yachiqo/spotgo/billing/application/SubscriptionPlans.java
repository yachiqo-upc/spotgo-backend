package com.yachiqo.spotgo.billing.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.math.*;
import static com.yachiqo.spotgo.billing.domain.model.valueobjects.BillingValues.*;
/** Server-side catalog supplied by configuration, never by the payer. No extra table. */
@Component
public class SubscriptionPlans {
 private final Map<String,Map<String,Object>> plans;
 public SubscriptionPlans(@Value("${app.billing.plans-json:[]}") String configuration,org.springframework.core.env.Environment environment){
  try{
   var mapper=new ObjectMapper();
   var rows=mapper.readTree(configuration);if(!rows.isArray())throw new IllegalArgumentException();
   if(rows.isEmpty()&&environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev","local"))){
    try(var input=new org.springframework.core.io.ClassPathResource("db.json").getInputStream()){
     var local=mapper.createArrayNode();for(var subscription:mapper.readTree(input).path("subscriptions"))local.add(subscription.path("plan"));rows=local;
    }
   }
   var catalog=new LinkedHashMap<String,Map<String,Object>>();
   for(var row:rows){
    Map<String,Object> plan=mapper.convertValue(row,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
    String code=text((String)plan.get("code")),audience=text((String)plan.get("audience"));
    if(!Set.of("DRIVER","PARKING_ADMIN").contains(audience))throw new IllegalArgumentException("Audiencia de plan invalida");
    positive(new BigDecimal(plan.get("price").toString()));
    int months=new BigDecimal(plan.get("billing_period_months").toString()).intValueExact();
    if(months<1||months>120)throw new IllegalArgumentException("Periodo de plan invalido");
    if(audience.equals("DRIVER")){
     BigDecimal discount=new BigDecimal(plan.getOrDefault("discount_percent",0).toString());
     if(discount.signum()<0||discount.compareTo(BigDecimal.valueOf(100))>=0)throw new IllegalArgumentException("Descuento debe estar entre 0 y menos de 100");
    }else if(new BigDecimal(plan.get("included_sensors").toString()).intValueExact()<1)throw new IllegalArgumentException("Cantidad de sensores invalida");
    if(catalog.putIfAbsent(code,Collections.unmodifiableMap(plan))!=null)throw new IllegalArgumentException("Codigo de plan duplicado");
   }
   plans=Collections.unmodifiableMap(catalog);
  }catch(Exception e){throw new IllegalArgumentException("app.billing.plans-json debe ser un catalogo JSON valido con code, audience, price y billing_period_months",e);}
 }
 public Collection<Map<String,Object>> all(){return plans.values();}
 public Map<String,Object> get(String code){
  var plan=plans.get(code);if(plan==null)throw new BillingException(HttpStatus.BAD_REQUEST,"Plan no configurado en el catalogo del servidor");return plan;
 }
 public static java.time.Instant periodEnd(com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription subscription){
  Object months=subscription.getPlan().get("billing_period_months");
  if(months==null)throw new IllegalArgumentException("El plan no define billing_period_months");
  return subscription.getStartAt().atZone(java.time.ZoneOffset.UTC).plusMonths(new BigDecimal(months.toString()).longValueExact()).toInstant();
 }
}
