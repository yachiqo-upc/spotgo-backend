package com.yachiqo.spotgo.iam.interfaces.rest.resources;
import com.yachiqo.spotgo.iam.domain.model.aggregates.RoleAssignment;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;
public record RoleAssignmentResource(UUID id, UUID accountId, UserRole roleType, UUID tenantId,
                                     Instant validFrom, Instant validTo, AssignmentStatus status, boolean effective) {
    public static RoleAssignmentResource from(RoleAssignment r, Instant now) {
        return new RoleAssignmentResource(r.getId(), r.getAccountId(), r.getRoleType(), r.getTenantId(),
            r.getValidFrom(), r.getValidTo(), r.getStatus(), r.isEffectiveAt(now));
    }
}
