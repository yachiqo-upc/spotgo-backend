package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.parking.domain.model.aggregates.ReservationChange;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationChangeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface ReservationChangeRepository extends JpaRepository<ReservationChange, UUID> {
    List<ReservationChange> findByStatus(com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationChangeStatus status);
    @Query("select c from ReservationChange c where c.reservation.id=:reservationId order by c.createdAt")
    List<ReservationChange> byReservation(@Param("reservationId") UUID reservationId);

    @Query("select c from ReservationChange c where c.paymentId=:paymentId")
    Optional<ReservationChange> byPayment(@Param("paymentId") UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReservationChange c where c.id=:id")
    Optional<ReservationChange> lock(@Param("id") UUID id);

    @Query("select c from ReservationChange c where c.reservation.spot.id=:spotId and c.status=:status " +
            "and c.startAt<:finish and c.endAt>:start")
    List<ReservationChange> overlapping(@Param("spotId") UUID spotId, @Param("start") Instant start,
                                        @Param("finish") Instant finish,
                                        @Param("status") ReservationChangeStatus status);

    @Query("select c from ReservationChange c where c.reservation.vehicle.id=:vehicleId and c.status=:status " +
            "and c.startAt<:finish and c.endAt>:start")
    List<ReservationChange> overlappingVehicle(@Param("vehicleId") UUID vehicleId, @Param("start") Instant start,
                                               @Param("finish") Instant finish,
                                               @Param("status") ReservationChangeStatus status);

    @Query("select c from ReservationChange c where c.status=:status and c.refundDueAt<=:now order by c.refundDueAt")
    List<ReservationChange> dueRefunds(@Param("status") ReservationChangeStatus status, @Param("now") Instant now);
}
