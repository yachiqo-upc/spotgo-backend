package com.yachiqo.spotgo.iam.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.iam.domain.model.aggregates.PasswordResetCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, String> {
    Optional<PasswordResetCode> findByEmail(String email);
}
