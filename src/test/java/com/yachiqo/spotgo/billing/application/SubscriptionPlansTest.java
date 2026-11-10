package com.yachiqo.spotgo.billing.application;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionPlansTest {
 private static final String PLAN="{\"code\":\"DRIVER_TEST\",\"audience\":\"DRIVER\",\"price\":10,\"billing_period_months\":1,\"discount_percent\":10}";
 @Test void productionDoesNotInventPlansWhenCatalogIsEmpty(){
  var environment=new MockEnvironment();environment.setActiveProfiles("production");
  assertTrue(new SubscriptionPlans("[]",environment).all().isEmpty());
 }
 @Test void localCatalogUsesOnlyExplicitDemoFixtures(){
  var environment=new MockEnvironment();environment.setActiveProfiles("dev");
  var catalog=new SubscriptionPlans("[]",environment);
  assertEquals(2,catalog.all().size());
  assertEquals("DRIVER",catalog.get("DRIVER_DEMO").get("audience"));
  assertEquals("PARKING_ADMIN",catalog.get("TENANT_DEMO").get("audience"));
 }
 @Test void duplicateCodesAreRejected(){
  assertThrows(IllegalArgumentException.class,()->new SubscriptionPlans("["+PLAN+","+PLAN+"]",new MockEnvironment()));
 }
 @Test void invalidDiscountAndPeriodAreRejected(){
  assertThrows(IllegalArgumentException.class,()->new SubscriptionPlans("["+PLAN.replace("discount_percent\":10","discount_percent\":100")+"]",new MockEnvironment()));
  assertThrows(IllegalArgumentException.class,()->new SubscriptionPlans("["+PLAN.replace("billing_period_months\":1","billing_period_months\":0")+"]",new MockEnvironment()));
 }
}
