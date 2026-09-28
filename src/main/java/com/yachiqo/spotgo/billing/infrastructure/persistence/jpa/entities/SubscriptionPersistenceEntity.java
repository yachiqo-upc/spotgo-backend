package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.entities;

import com.yachiqo.spotgo.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "subscriptions", indexes = {
    @Index(name = "idx_subscription_clientId", columnList = "clientId")
})
public class SubscriptionPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String planId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String renewsOn;

    @Column(nullable = false)
    private Double pricePerMonth;

    @Column(nullable = false)
    private Integer sessions;

    @Column(nullable = false)
    private Double savedThisMonth;

    @Column
    private String savingsMonth;

    @Column(nullable = false)
    private String memberSince;

    @Column(nullable = false)
    private Boolean autoRenewal;

    @Column(nullable = false, length = 4)
    private String paymentMethodLastFour;

    @Column(nullable = false)
    private String paymentMethodExpiry;

    public SubscriptionPersistenceEntity() {
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRenewsOn() { return renewsOn; }
    public void setRenewsOn(String renewsOn) { this.renewsOn = renewsOn; }

    public Double getPricePerMonth() { return pricePerMonth; }
    public void setPricePerMonth(Double pricePerMonth) { this.pricePerMonth = pricePerMonth; }

    public Integer getSessions() { return sessions; }
    public void setSessions(Integer sessions) { this.sessions = sessions; }

    public Double getSavedThisMonth() { return savedThisMonth; }
    public void setSavedThisMonth(Double savedThisMonth) { this.savedThisMonth = savedThisMonth; }

    public String getSavingsMonth() { return savingsMonth; }
    public void setSavingsMonth(String savingsMonth) { this.savingsMonth = savingsMonth; }

    public String getMemberSince() { return memberSince; }
    public void setMemberSince(String memberSince) { this.memberSince = memberSince; }

    public Boolean getAutoRenewal() { return autoRenewal; }
    public void setAutoRenewal(Boolean autoRenewal) { this.autoRenewal = autoRenewal; }

    public String getPaymentMethodLastFour() { return paymentMethodLastFour; }
    public void setPaymentMethodLastFour(String paymentMethodLastFour) { this.paymentMethodLastFour = paymentMethodLastFour; }

    public String getPaymentMethodExpiry() { return paymentMethodExpiry; }
    public void setPaymentMethodExpiry(String paymentMethodExpiry) { this.paymentMethodExpiry = paymentMethodExpiry; }
}
