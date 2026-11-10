package com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.iam.domain.model.aggregates.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {
    @Query("select r from RoleAssignment r where r.account.id = :accountId")
    Optional<RoleAssignment> findByAccountId(@Param("accountId") UUID accountId);

    boolean existsByRoleType(com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole roleType);
}
