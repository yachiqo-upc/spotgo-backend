package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Tenant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface TenantRepository extends JpaRepository<Tenant,UUID> { @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from Tenant t where t.id=:id") Optional<Tenant> lock(@Param("id") UUID id); }
