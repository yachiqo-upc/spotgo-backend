package com.yachiqo.spotgo.billing.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yachiqo.spotgo.billing.application.commandservices.ReceiptCommandService;
import com.yachiqo.spotgo.billing.application.queryservices.ReceiptQueryService;
import com.yachiqo.spotgo.billing.domain.model.aggregates.Receipt;
import com.yachiqo.spotgo.billing.domain.model.queries.GetReceiptByIdQuery;
import com.yachiqo.spotgo.billing.domain.repositories.ReceiptRepository;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.ReceiptStatus;
import com.yachiqo.spotgo.billing.interfaces.rest.resources.CreateReceiptResource;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ReceiptsControllerTest {

    @Mock
    private ReceiptCommandService receiptCommandService;
    @Mock
    private ReceiptQueryService receiptQueryService;
    @Mock
    private ReceiptRepository receiptRepository;

    private ReceiptsController controller;

    @BeforeEach
    void setUp() {
        controller = new ReceiptsController(receiptCommandService, receiptQueryService, receiptRepository);
    }

    @Test
    void createReceiptIsRejectedBecauseReceiptsAreGeneratedServerSide() {
        var principal = new SpotgoUserPrincipal("client-auth", "client@spotgo.com", "pw", UserRole.CLIENT, 0L);
        var resource = new CreateReceiptResource(
                "body-client",
                "res-1",
                "INV-001",
                "SpotGo Center",
                "2026-07-08",
                1,
                30,
                "Visa 4242",
                10.0);

        var response = controller.createReceipt(principal, resource);

        assertEquals(405, response.getStatusCode().value());
    }

    @Test
    void getAllReceiptsReturnsOnlyAuthenticatedClientReceipts() {
        var principal = new SpotgoUserPrincipal("client-auth", "client@spotgo.com", "pw", UserRole.CLIENT, 0L);
        when(receiptRepository.findAllByClientId("client-auth")).thenReturn(List.of(
                new Receipt("receipt-1", "client-auth", "res-1", "INV-001", "SpotGo Center", "2026-07-08", 1, 30, "Visa 4242", 10.0, ReceiptStatus.PENDING)));

        var response = controller.getAllReceipts(principal, null);

        assertEquals(1, response.getBody().size());
        assertEquals("client-auth", response.getBody().get(0).clientId());
    }

    @Test
    void getReceiptByIdRejectsForeignReceipt() {
        var principal = new SpotgoUserPrincipal("client-auth", "client@spotgo.com", "pw", UserRole.CLIENT, 0L);
        when(receiptQueryService.handle(any(GetReceiptByIdQuery.class))).thenReturn(Optional.of(
                new Receipt("receipt-1", "other-client", "res-1", "INV-001", "SpotGo Center", "2026-07-08", 1, 30, "Visa 4242", 10.0, ReceiptStatus.PENDING)));

        assertThrows(AccessDeniedException.class, () -> controller.getReceiptById(principal, "receipt-1"));
    }
}
