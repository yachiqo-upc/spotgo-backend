package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {
    List<Reservation> findByParkingId(String parkingId);
    List<Reservation> findByClientId(String clientId);
    List<Reservation> findByParkingIdAndSpot(String parkingId, String spot);
    List<Reservation> findByParkingIdAndStatus(String parkingId, ReservationStatus status);
    List<Reservation> findByStatusAndEndDateBefore(ReservationStatus status, LocalDateTime endDate);
}
