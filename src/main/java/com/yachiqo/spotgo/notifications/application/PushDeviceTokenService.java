package com.yachiqo.spotgo.notifications.application;

import com.yachiqo.spotgo.iam.domain.model.aggregates.Account;
import com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.notifications.domain.model.DevicePlatform;
import com.yachiqo.spotgo.notifications.domain.model.PushDeviceToken;
import com.yachiqo.spotgo.notifications.infrastructure.persistence.PushDeviceTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PushDeviceTokenService {
    private final PushDeviceTokenRepository devices;
    private final AccountRepository accounts;
    private final Clock clock;

    public PushDeviceTokenService(PushDeviceTokenRepository devices, AccountRepository accounts, Clock clock) {
        this.devices = devices;
        this.accounts = accounts;
        this.clock = clock;
    }

    public List<PushDeviceToken> ownDevices(SpotgoUserPrincipal actor) {
        return devices.findAllByAccount_IdOrderByLastSeenAtDesc(actor.getAccountId());
    }

    @Transactional
    public PushDeviceToken register(SpotgoUserPrincipal actor, String installationId, DevicePlatform platform) {
        if (installationId == null || installationId.isBlank() || installationId.length() > 256 || platform == null)
            throw new IllegalArgumentException("A valid Firebase installation ID and platform are required.");
        String normalized = installationId.trim();
        Account account = accounts.findById(actor.getAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The account is no longer available."));
        var registration = devices.findByInstallationId(normalized).orElse(null);
        if (registration == null) registration = new PushDeviceToken(account, normalized, platform, clock.instant());
        else registration.register(account, normalized, platform, clock.instant());
        return devices.saveAndFlush(registration);
    }

    @Transactional
    public void remove(SpotgoUserPrincipal actor, UUID deviceTokenId) {
        var registration = devices.findByIdAndAccount_Id(deviceTokenId, actor.getAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device registration not found."));
        devices.delete(registration);
    }
}
