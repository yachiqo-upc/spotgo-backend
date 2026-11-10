package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import com.yachiqo.spotgo.iam.domain.model.aggregates.Account;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
public record AccountResource(@Schema(description="UUID de accounts.account_id") UUID id, String email,
                              AccountStatus status, Instant createdAt, Instant updatedAt) {
    public static AccountResource from(Account a) { return new AccountResource(a.getId(), a.getEmail(), a.getStatus(), a.getCreatedAt(), a.getUpdatedAt()); }
}
