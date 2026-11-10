package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import com.yachiqo.spotgo.iam.domain.model.aggregates.Session;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.SessionStatus;
import java.time.Instant;
import java.util.UUID;
/** Never exposes refresh_token_hash or raw tokens. */
public record SessionResource(UUID id, UUID accountId, Instant lastActivityAt, SessionStatus status,
                              Instant revokedAt, Instant createdAt, Instant expiresAt, boolean usable) {
    public static SessionResource from(Session s, Instant now) {
        return new SessionResource(s.getId(), s.getAccountId(), s.getLastActivityAt(), s.getStatus(),
            s.getRevokedAt(), s.getCreatedAt(), s.getExpiresAt(), s.isUsableAt(now));
    }
}
