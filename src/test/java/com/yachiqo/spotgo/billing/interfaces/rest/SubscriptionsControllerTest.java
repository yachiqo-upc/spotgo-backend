package com.yachiqo.spotgo.billing.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yachiqo.spotgo.billing.application.commandservices.SubscriptionCommandService;
import com.yachiqo.spotgo.billing.application.queryservices.SubscriptionQueryService;
import com.yachiqo.spotgo.billing.domain.model.aggregates.Subscription;
import com.yachiqo.spotgo.billing.domain.model.commands.CreateSubscriptionCommand;
import com.yachiqo.spotgo.billing.domain.repositories.SubscriptionRepository;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.SubscriptionStatus;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.CreateSubscriptionResource;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.UpdateSubscriptionResource;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.shared.application.result.Result;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class SubscriptionsControllerTest {

    @Mock
    private SubscriptionCommandService subscriptionCommandService;
    @Mock
    private SubscriptionQueryService subscriptionQueryService;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    private SubscriptionsController controller;

    @BeforeEach
    void setUp() {
        controller = new SubscriptionsController(subscriptionCommandService, subscriptionQueryService, subscriptionRepository);
    }

    @Test
    void createSubscriptionUsesAuthenticatedClientId() {
        var principal = new SpotgoUserPrincipal(java.util.UUID.fromString("00000000-0000-4000-8000-000000000902"), "client@spotgo.com", UserRole.DRIVER, null, null);
        var resource = new CreateSubscriptionResource("plan-pro", true, "4242", "12/28");
        when(subscriptionCommandService.handle(any(CreateSubscriptionCommand.class))).thenReturn(Result.success(
                new Subscription("sub-1", "00000000-0000-4000-8000-000000000902", "plan-pro", SubscriptionStatus.ACTIVE, "2026-08-01", 29.9, 0, 0.0, "", "2026-07-01", true, "4242", "12/28")));

        controller.createSubscription(principal, resource);

        var captor = ArgumentCaptor.forClass(CreateSubscriptionCommand.class);
        verify(subscriptionCommandService).handle(captor.capture());
        assertEquals("00000000-0000-4000-8000-000000000902", captor.getValue().clientId());
    }

    @Test
    void getAllSubscriptionsReturnsOnlyAuthenticatedClientSubscriptions() {
        var principal = new SpotgoUserPrincipal(java.util.UUID.fromString("00000000-0000-4000-8000-000000000902"), "client@spotgo.com", UserRole.DRIVER, null, null);
        when(subscriptionRepository.findAllByClientId("00000000-0000-4000-8000-000000000902")).thenReturn(List.of(
                new Subscription("sub-1", "00000000-0000-4000-8000-000000000902", "plan-pro", SubscriptionStatus.ACTIVE, "2026-08-01", 29.9, 0, 0.0, "", "2026-07-01", true, "4242", "12/28")));

        var response = controller.getAllSubscriptions(principal);

        assertEquals(1, response.getBody().size());
        assertEquals("00000000-0000-4000-8000-000000000902", response.getBody().get(0).clientId());
    }

    @Test
    void updateSubscriptionRejectsForeignOwner() {
        var principal = new SpotgoUserPrincipal(java.util.UUID.fromString("00000000-0000-4000-8000-000000000902"), "client@spotgo.com", UserRole.DRIVER, null, null);
        var resource = new UpdateSubscriptionResource("plan-pro", true, "4242", "12/28");
        when(subscriptionRepository.findById("sub-1")).thenReturn(Optional.of(
                new Subscription("sub-1", "other-client", "plan-pro", SubscriptionStatus.ACTIVE, "2026-08-01", 29.9, 0, 0.0, "", "2026-07-01", true, "4242", "12/28")));

        assertThrows(AccessDeniedException.class, () -> controller.updateSubscription(principal, "sub-1", resource));
    }
}
