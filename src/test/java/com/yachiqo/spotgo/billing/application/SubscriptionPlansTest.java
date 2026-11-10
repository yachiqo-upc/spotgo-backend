package com.yachiqo.spotgo.billing.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionPlansTest {
    private final SubscriptionPlans plans = new SubscriptionPlans();

    @Test
    void exposesFixedDriverAndTenantPlans() {
        assertEquals(3, plans.all().size());
        assertEquals(new BigDecimal("15.00"), plans.get("DRIVER_MONTHLY").get("price"));
        assertEquals(10, new BigDecimal(plans.get("DRIVER_MONTHLY").get("discount_percent").toString()).intValueExact());
        assertEquals(new BigDecimal("160.00"), plans.get("DRIVER_ANNUAL").get("price"));
        assertEquals(15, new BigDecimal(plans.get("DRIVER_ANNUAL").get("discount_percent").toString()).intValueExact());
        assertEquals("PARKING_ADMIN", plans.get("TENANT_MONTHLY").get("audience"));
    }

    @Test
    void tenantPriceUsesEveryRegisteredParkingSpot() {
        var spots = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var snapshot = plans.snapshotForTenant(plans.get("TENANT_MONTHLY"), spots);

        assertEquals(new BigDecimal("34.00"), snapshot.get("price"));
        assertEquals(4, snapshot.get("parking_spot_count"));
        assertEquals(4, plans.licensedSpotIds(snapshot).size());
    }

    @Test
    void rejectsNegativeParkingSpotCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> plans.tenantMonthlyPrice(plans.get("TENANT_MONTHLY"), -1));
    }
}
