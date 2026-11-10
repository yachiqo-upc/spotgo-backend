package com.yachiqo.spotgo.billing.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

import static com.yachiqo.spotgo.billing.domain.model.valueobjects.BillingValues.amount;
import static com.yachiqo.spotgo.billing.domain.model.valueobjects.BillingValues.positive;

/** Fixed server-side subscription catalog, never supplied by the payer. */
@Component
public class SubscriptionPlans {
    public static final String APP_USAGE_PRICE = "app_usage_price";
    public static final String PRICE_PER_IOT_DEVICE = "price_per_iot_device";
    public static final BigDecimal TENANT_APP_USAGE_PRICE = new BigDecimal("30.00");
    public static final BigDecimal TENANT_PRICE_PER_PARKING_SPOT = new BigDecimal("1.00");
    private static final String LICENSED_SPOT_IDS = "licensed_spot_ids";

    private final Map<String, Map<String, Object>> plans;

    public SubscriptionPlans() {
        var catalog = new LinkedHashMap<String, Map<String, Object>>();
        addStandardDriverPlans(catalog);
        catalog.put("TENANT_MONTHLY", fixedTenantPlan());
        plans = Collections.unmodifiableMap(catalog);
    }

    private static void addStandardDriverPlans(Map<String, Map<String, Object>> catalog) {
        catalog.put("DRIVER_MONTHLY", Map.of(
                "code", "DRIVER_MONTHLY",
                "audience", "DRIVER",
                "billing_period_months", 1,
                "price", new BigDecimal("15.00"),
                "discount_percent", 10));
        catalog.put("DRIVER_ANNUAL", Map.of(
                "code", "DRIVER_ANNUAL",
                "audience", "DRIVER",
                "billing_period_months", 12,
                "price", new BigDecimal("160.00"),
                "discount_percent", 15));
    }

    private static Map<String, Object> fixedTenantPlan() {
        return Map.of(
                "code", "TENANT_MONTHLY",
                "audience", "PARKING_ADMIN",
                "billing_period_months", 1,
                APP_USAGE_PRICE, TENANT_APP_USAGE_PRICE,
                PRICE_PER_IOT_DEVICE, TENANT_PRICE_PER_PARKING_SPOT);
    }

    public Collection<Map<String, Object>> all() {
        return plans.values();
    }

    public Map<String, Object> get(String code) {
        var plan = plans.get(code);
        if (plan == null) throw new BillingException(HttpStatus.BAD_REQUEST, "Plan is not configured in the server catalog.");
        return plan;
    }

    public boolean isSpotPricedTenantPlan(Map<String, Object> plan) {
        return "PARKING_ADMIN".equals(plan.get("audience"))
                && plan.containsKey(APP_USAGE_PRICE)
                && plan.containsKey(PRICE_PER_IOT_DEVICE);
    }

    public boolean hasSpotPricing(Map<String, Object> plan) {
        return plan.containsKey(APP_USAGE_PRICE) || plan.containsKey(PRICE_PER_IOT_DEVICE);
    }

    public BigDecimal pricePerIotDevice(Map<String, Object> plan) {
        return amount(new BigDecimal(plan.get(PRICE_PER_IOT_DEVICE).toString()));
    }

    public BigDecimal tenantMonthlyPrice(Map<String, Object> plan, long parkingSpotCount) {
        if (!isSpotPricedTenantPlan(plan)) {
            return positive(new BigDecimal(plan.get("price").toString()));
        }
        if (parkingSpotCount < 0) throw new IllegalArgumentException("Invalid number of parking spots.");
        BigDecimal appUsagePrice = positive(new BigDecimal(plan.get(APP_USAGE_PRICE).toString()));
        return positive(amount(appUsagePrice.add(
                pricePerIotDevice(plan).multiply(BigDecimal.valueOf(parkingSpotCount)))));
    }

    public Map<String, Object> snapshotForTenant(Map<String, Object> plan, Collection<UUID> parkingSpotIds) {
        if (!isSpotPricedTenantPlan(plan)) return plan;

        var sortedSpotIds = parkingSpotIds.stream().map(UUID::toString).sorted().toList();
        var snapshot = new LinkedHashMap<>(plan);
        snapshot.put("price", tenantMonthlyPrice(plan, sortedSpotIds.size()));
        snapshot.put("parking_spot_count", sortedSpotIds.size());
        snapshot.put("included_sensors", sortedSpotIds.size());
        snapshot.put(LICENSED_SPOT_IDS, sortedSpotIds);
        return Collections.unmodifiableMap(snapshot);
    }

    public Set<String> licensedSpotIds(Map<String, Object> plan) {
        Object ids = plan.get(LICENSED_SPOT_IDS);
        if (!(ids instanceof Collection<?> values)) return Set.of();
        var result = new LinkedHashSet<String>();
        for (Object value : values) if (value != null) result.add(value.toString());
        return Collections.unmodifiableSet(result);
    }

    public static java.time.Instant periodEnd(com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription subscription) {
        Object months = subscription.getPlan().get("billing_period_months");
        if (months == null) throw new IllegalArgumentException("The plan does not define billing_period_months.");
        return subscription.getStartAt().atZone(java.time.ZoneOffset.UTC)
                .plusMonths(new BigDecimal(months.toString()).longValueExact()).toInstant();
    }
}
