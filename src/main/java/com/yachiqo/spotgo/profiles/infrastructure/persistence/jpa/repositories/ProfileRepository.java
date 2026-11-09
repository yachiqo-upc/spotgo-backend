package com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Optional<Profile> findByAccountId(UUID accountId);
    boolean existsByAccountId(UUID accountId);
}
