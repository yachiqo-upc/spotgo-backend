package com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.iam.domain.model.aggregates.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);
    // Temporary boundary for the unmigrated Parking module; database identifiers remain native UUID.
    default boolean existsById(String id) {
        try { return existsById(UUID.fromString(id)); } catch (IllegalArgumentException ex) { return false; }
    }
}
