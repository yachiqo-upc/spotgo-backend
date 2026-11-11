package com.yachiqo.spotgo.parking.application;

import com.yachiqo.spotgo.iam.application.AccountAdministrationService;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.application.internal.commandservices.ParkingCommandServiceImpl;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class TenantOnboardingService {
    private final ParkingCommandServiceImpl parking;
    private final AccountAdministrationService accounts;

    public TenantOnboardingService(ParkingCommandServiceImpl parking, AccountAdministrationService accounts) {
        this.parking = parking;
        this.accounts = accounts;
    }

    public record Result(Tenant tenant, AccountAdministrationService.ProvisionedParkingAdmin parkingAdmin) {}

    @Transactional
    public Result onboard(SpotgoUserPrincipal actor, String tenantName, String address,
                          Map<String, Object> coordinates, Map<String, Object> contactData,
                          Map<String, Object> rules, String adminEmail, String firstName,
                          String lastName, String phone, String source) {
        Tenant tenant = parking.createTenant(actor, tenantName, address, coordinates, contactData, rules);
        var admin = accounts.provisionParkingAdmin(actor, tenant.getId(), adminEmail, firstName, lastName, phone, source);
        return new Result(tenant, admin);
    }
}
