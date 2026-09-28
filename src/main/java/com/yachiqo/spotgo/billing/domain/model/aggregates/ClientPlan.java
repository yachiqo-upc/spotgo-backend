package com.yachiqo.spotgo.billing.domain.model.aggregates;

import com.yachiqo.spotgo.billing.domain.model.valueobjects.PlanType;
import com.yachiqo.spotgo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ClientPlan extends AbstractDomainAggregateRoot<ClientPlan> {

    @Getter
    @Setter
    private String id;

    @Getter
    private PlanType type;

    @Getter
    private String name;

    @Getter
    private Double monthlyPrice;

    @Getter
    private String description;

    @Getter
    private Integer reservationsPerMonth; // null means unlimited

    @Getter
    private Double discountPercent;

    @Getter
    private List<String> features;

    public ClientPlan(String id, PlanType type, String name, Double monthlyPrice,
                      String description, Integer reservationsPerMonth,
                      Double discountPercent, List<String> features) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.monthlyPrice = monthlyPrice;
        this.description = description;
        this.reservationsPerMonth = reservationsPerMonth;
        this.discountPercent = discountPercent;
        this.features = features;
    }

    public ClientPlan(PlanType type, String name, Double monthlyPrice,
                      String description, Integer reservationsPerMonth,
                      Double discountPercent, List<String> features) {
        this(null, type, name, monthlyPrice, description, reservationsPerMonth, discountPercent, features);
    }
}
