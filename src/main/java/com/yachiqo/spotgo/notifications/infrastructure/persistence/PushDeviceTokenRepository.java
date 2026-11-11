package com.yachiqo.spotgo.notifications.infrastructure.persistence;

import com.yachiqo.spotgo.notifications.domain.model.PushDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, UUID> {
    Optional<PushDeviceToken> findByToken(String token);

    Optional<PushDeviceToken> findByIdAndAccount_Id(UUID id, UUID accountId);

    List<PushDeviceToken> findAllByAccount_IdOrderByLastSeenAtDesc(UUID accountId);

    @Query("select d from PushDeviceToken d where d.account.id in :accountIds")
    List<PushDeviceToken> findAllForAccounts(@Param("accountIds") Collection<UUID> accountIds);
}
