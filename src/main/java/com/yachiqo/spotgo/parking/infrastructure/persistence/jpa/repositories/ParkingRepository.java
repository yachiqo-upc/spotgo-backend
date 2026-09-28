package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.parking.domain.model.aggregates.Parking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParkingRepository extends JpaRepository<Parking, String> {
    Optional<Parking> findByAdminId(String adminId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parking p WHERE p.id = :parkingId")
    Optional<Parking> findByIdForUpdate(@Param("parkingId") String parkingId);
}
