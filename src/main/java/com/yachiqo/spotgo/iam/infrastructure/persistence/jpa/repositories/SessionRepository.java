package com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.iam.domain.model.aggregates.Session;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface SessionRepository extends JpaRepository<Session, UUID> {
    @EntityGraph(attributePaths = "account")
    Optional<Session> findById(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "account")
    Optional<Session> findByRefreshTokenHash(String hash);
    @Query("select s from Session s where s.account.id = :accountId order by s.createdAt desc")
    List<Session> findAllByAccountIdOrderByCreatedAtDesc(@Param("accountId") UUID accountId);
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("update Session s set s.status = :revoked, s.revokedAt = :now where s.account.id = :accountId and s.status = :active")
    int revokeAll(@Param("accountId") UUID accountId, @Param("now") Instant now,
                  @Param("active") SessionStatus active, @Param("revoked") SessionStatus revoked);
}
