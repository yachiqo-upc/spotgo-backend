package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities;

import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "clientPlans")
public class ClientPlanPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double monthlyPrice;

    @Column(nullable = false, length = 500)
    private String description;

    @Column
    private Integer reservationsPerMonth;

    @Column(nullable = false)
    private Double discountPercent;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "clientPlanFeatures", joinColumns = @JoinColumn(name = "clientPlanId"))
    @Column(name = "feature")
    private List<String> features;

    public ClientPlanPersistenceEntity() {
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(Double monthlyPrice) { this.monthlyPrice = monthlyPrice; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getReservationsPerMonth() { return reservationsPerMonth; }
    public void setReservationsPerMonth(Integer reservationsPerMonth) { this.reservationsPerMonth = reservationsPerMonth; }

    public Double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }

    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }
}
