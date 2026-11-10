package com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Profile p where p.id=:id")
    java.util.Optional<Profile> lock(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<Profile> findByAccountId(UUID accountId);
    boolean existsByAccountId(UUID accountId);
}
