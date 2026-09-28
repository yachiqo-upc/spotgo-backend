package com.yachiqo.spotgo.billing.domain.model.aggregates;

import com.yachiqo.spotgo.billing.domain.model.commands.CreateSubscriptionCommand;
import com.yachiqo.spotgo.billing.domain.model.commands.PatchSubscriptionSavingsCommand;
import com.yachiqo.spotgo.billing.domain.model.commands.UpdateSubscriptionCommand;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.SubscriptionStatus;
import com.yachiqo.spotgo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.Setter;

public class Subscription extends AbstractDomainAggregateRoot<Subscription> {

    @Getter
    @Setter
    private String id;

    @Getter
    private String clientId;

    @Getter
    @Setter
    private String planId;

    @Getter
    @Setter
    private SubscriptionStatus status;

    @Getter
    @Setter
    private String renewsOn;

    @Getter
    @Setter
    private Double pricePerMonth;

    @Getter
    @Setter
    private Integer sessions;

    @Getter
    @Setter
    private Double savedThisMonth;

    @Getter
    @Setter
    private String savingsMonth;

    @Getter
    private String memberSince;

    @Getter
    @Setter
    private Boolean autoRenewal;

    @Getter
    @Setter
    private String paymentMethodLastFour;

    @Getter
    @Setter
    private String paymentMethodExpiry;

    public Subscription(String id, String clientId, String planId, SubscriptionStatus status,
                        String renewsOn, Double pricePerMonth, Integer sessions,
                        Double savedThisMonth, String savingsMonth, String memberSince,
                        Boolean autoRenewal, String paymentMethodLastFour, String paymentMethodExpiry) {
        this.id = id;
        this.clientId = clientId;
        this.planId = planId;
        this.status = status;
        this.renewsOn = renewsOn;
        this.pricePerMonth = pricePerMonth;
        this.sessions = sessions;
        this.savedThisMonth = savedThisMonth;
        this.savingsMonth = savingsMonth;
        this.memberSince = memberSince;
        this.autoRenewal = autoRenewal;
        this.paymentMethodLastFour = paymentMethodLastFour;
        this.paymentMethodExpiry = paymentMethodExpiry;
    }

    public Subscription(String clientId, String planId, SubscriptionStatus status,
                        String renewsOn, Double pricePerMonth, Integer sessions,
                        Double savedThisMonth, String savingsMonth, String memberSince,
                        Boolean autoRenewal, String paymentMethodLastFour, String paymentMethodExpiry) {
        this(null, clientId, planId, status, renewsOn, pricePerMonth, sessions,
                savedThisMonth, savingsMonth, memberSince, autoRenewal, paymentMethodLastFour, paymentMethodExpiry);
    }

    public Subscription(CreateSubscriptionCommand command) {
        this(command.clientId(), command.planId(), SubscriptionStatus.ACTIVE,
                "", 0.0, 0,
                0.0, "", "", command.autoRenewal(),
                command.paymentMethodLastFour(), command.paymentMethodExpiry());
    }

    public void applyPlan(String planId, Double pricePerMonth, String renewsOn) {
        this.planId = planId;
        this.pricePerMonth = pricePerMonth;
        this.renewsOn = renewsOn;
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void update(UpdateSubscriptionCommand command, Double pricePerMonth, String renewsOn) {
        this.planId = command.planId();
        this.status = SubscriptionStatus.ACTIVE;
        this.renewsOn = renewsOn;
        this.pricePerMonth = pricePerMonth;
        this.autoRenewal = command.autoRenewal();
        this.paymentMethodLastFour = command.paymentMethodLastFour();
        this.paymentMethodExpiry = command.paymentMethodExpiry();
    }

    public void registerReservation(String currentMonth, double savingsAmount) {
        if (currentMonth == null || currentMonth.isBlank()) {
            throw new IllegalArgumentException("Savings month is required");
        }
        if (!currentMonth.equals(this.savingsMonth)) {
            this.savingsMonth = currentMonth;
            this.savedThisMonth = 0.0;
            this.sessions = 0;
        }
        this.sessions = this.sessions == null ? 1 : this.sessions + 1;
        this.savedThisMonth = (this.savedThisMonth == null ? 0.0 : this.savedThisMonth) + Math.max(0.0, savingsAmount);
    }

    public void patchSavings(PatchSubscriptionSavingsCommand command) {
        this.savedThisMonth = command.savedThisMonth();
        this.savingsMonth = command.savingsMonth();
    }
}
