package com.yachiqo.spotgo.billing.application.internal.commandservices;

import com.yachiqo.spotgo.billing.application.commandservices.SubscriptionCommandService;
import com.yachiqo.spotgo.billing.domain.model.aggregates.ClientPlan;
import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import com.yachiqo.spotgo.billing.domain.model.commands.CreateSubscriptionCommand;
import com.yachiqo.spotgo.billing.domain.model.commands.PatchSubscriptionSavingsCommand;
import com.yachiqo.spotgo.billing.domain.model.commands.UpdateSubscriptionCommand;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.PlanType;
import com.yachiqo.spotgo.billing.domain.repositories.ClientPlanRepository;
import com.yachiqo.spotgo.billing.domain.repositories.SubscriptionRepository;
import com.yachiqo.spotgo.shared.application.result.ApplicationError;
import com.yachiqo.spotgo.shared.application.result.Result;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionCommandServiceImpl implements SubscriptionCommandService {

    private final SubscriptionRepository subscriptionRepository;
    private final ClientPlanRepository clientPlanRepository;
    private final Clock clock;

    public SubscriptionCommandServiceImpl(SubscriptionRepository subscriptionRepository,
                                          ClientPlanRepository clientPlanRepository,
                                          Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clientPlanRepository = clientPlanRepository;
        this.clock = clock;
    }

    @Override
    public Result<Subscription, ApplicationError> handle(CreateSubscriptionCommand command) {
        try {
            if (!subscriptionRepository.findAllByClientId(command.clientId()).isEmpty()) {
                return Result.failure(ApplicationError.conflict("subscription", "Client already has a subscription"));
            }
            var plan = requirePlan(command.planId());
            var subscription = new Subscription(command);
            subscription.applyPlan(plan.getId(), safePrice(plan), computeRenewsOn(plan));
            subscription.patchSavings(new PatchSubscriptionSavingsCommand(subscription.getId(), 0.0, currentYearMonth()));
            if (subscription.getMemberSince() == null || subscription.getMemberSince().isBlank()) {
                var created = new Subscription(
                        subscription.getId(), subscription.getClientId(), subscription.getPlanId(), subscription.getStatus(),
                        subscription.getRenewsOn(), subscription.getPricePerMonth(), subscription.getSessions(),
                        subscription.getSavedThisMonth(), subscription.getSavingsMonth(), LocalDate.now(clock).toString(),
                        subscription.getAutoRenewal(), subscription.getPaymentMethodLastFour(), subscription.getPaymentMethodExpiry());
                subscription = created;
            }
            var saved = subscriptionRepository.save(subscription);
            return Result.success(saved);
        } catch (IllegalArgumentException e) {
            return Result.failure(ApplicationError.validationError("Subscription", e.getMessage()));
        } catch (Exception e) {
            return Result.failure(ApplicationError.unexpected("Subscription creation", e.getMessage()));
        }
    }

    @Override
    public Result<Subscription, ApplicationError> handle(UpdateSubscriptionCommand command) {
        try {
            var existing = subscriptionRepository.findById(command.subscriptionId());
            if (existing.isEmpty()) {
                return Result.failure(ApplicationError.notFound(
                        "Subscription", "Subscription with ID %s not found".formatted(command.subscriptionId())));
            }
            var subscription = existing.get();
            var plan = requirePlan(command.planId());
            subscription.update(command, safePrice(plan), computeRenewsOn(plan));
            var saved = subscriptionRepository.save(subscription);
            return Result.success(saved);
        } catch (IllegalArgumentException e) {
            return Result.failure(ApplicationError.validationError("Subscription", e.getMessage()));
        } catch (Exception e) {
            return Result.failure(ApplicationError.unexpected("Subscription update", e.getMessage()));
        }
    }

    @Override
    public Result<Subscription, ApplicationError> handle(PatchSubscriptionSavingsCommand command) {
        try {
            var existing = subscriptionRepository.findById(command.subscriptionId());
            if (existing.isEmpty()) {
                return Result.failure(ApplicationError.notFound(
                        "Subscription", "Subscription with ID %s not found".formatted(command.subscriptionId())));
            }
            var subscription = existing.get();
            subscription.patchSavings(command);
            var saved = subscriptionRepository.save(subscription);
            return Result.success(saved);
        } catch (Exception e) {
            return Result.failure(ApplicationError.unexpected("Subscription patch savings", e.getMessage()));
        }
    }

    private ClientPlan requirePlan(String planId) {
        return clientPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Client plan does not exist"));
    }

    private double safePrice(ClientPlan plan) {
        return plan.getMonthlyPrice() == null ? 0.0 : plan.getMonthlyPrice();
    }

    private String computeRenewsOn(ClientPlan plan) {
        var today = LocalDate.now(clock);
        if (plan.getType() == PlanType.ANNUAL) {
            return today.plusYears(1).toString();
        }
        if (plan.getType() == PlanType.MONTHLY) {
            return today.plusMonths(1).toString();
        }
        return today.toString();
    }

    private String currentYearMonth() {
        var today = LocalDate.now(clock);
        return "%d-%02d".formatted(today.getYear(), today.getMonthValue());
    }
}
