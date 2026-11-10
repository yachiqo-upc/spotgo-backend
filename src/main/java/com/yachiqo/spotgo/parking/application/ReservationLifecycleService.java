package com.yachiqo.spotgo.parking.application;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
@Service
public class ReservationLifecycleService {
 private final ReservationRepository reservations;private final Clock clock;
 public ReservationLifecycleService(ReservationRepository reservations,Clock clock){this.reservations=reservations;this.clock=clock;}
 @Scheduled(fixedDelayString="${app.parking.lock-cleanup-ms:30000}") @Transactional
 public void expireLocks() {
  for(var r:reservations.findAll()) if(r.getStatus()==ReservationStatus.PENDING_PAYMENT)
   reservations.lock(r.getId()).ifPresent(locked->locked.expire(clock.instant()));
 }
}
