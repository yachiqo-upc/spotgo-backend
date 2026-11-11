package com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.iam.domain.model.aggregates.RoleAssignment;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.AssignmentStatus;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {
    @Query("select r from RoleAssignment r where r.account.id = :accountId")
    Optional<RoleAssignment> findByAccountId(@Param("accountId") UUID accountId);

    boolean existsByRoleType(com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole roleType);

    @Query("select r.account.id from RoleAssignment r where r.tenantId = :tenantId and r.roleType = :role " +
            "and r.status = :status and r.validFrom <= :now and (r.validTo is null or r.validTo > :now)")
    List<UUID> findEffectiveAccountIdsByTenantAndRole(@Param("tenantId") UUID tenantId,
                                                       @Param("role") UserRole role,
                                                       @Param("status") AssignmentStatus status,
                                                       @Param("now") Instant now);
}
