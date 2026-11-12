package com.yachiqo.spotgo.parking.application;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationChangeRepository;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationStatus;
import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReservationChangeStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
@Service
public class ReservationLifecycleService {
 private final ReservationRepository reservations;private final ReservationChangeRepository changes;private final Clock clock;
 public ReservationLifecycleService(ReservationRepository reservations,ReservationChangeRepository changes,Clock clock){this.reservations=reservations;this.changes=changes;this.clock=clock;}
 @Scheduled(fixedDelayString="${app.parking.lock-cleanup-ms:30000}") @Transactional
 public void expireLocks() {
  var now=clock.instant();
  for(var r:reservations.findAll()) if(r.getStatus()==ReservationStatus.PENDING_PAYMENT)
   reservations.lock(r.getId()).ifPresent(locked->{
    if(locked.getParentReservationId()!=null){
     var root=locked;while(root.getParentReservation()!=null)root=root.getParentReservation();
     if(root.getStatus()!=ReservationStatus.ACTIVE||!now.isBefore(root.getEndAt()))locked.cancel();
     else locked.expire(now);
    }else locked.expire(now);
   });
  for(var change:changes.findByStatus(ReservationChangeStatus.PENDING_PAYMENT)){
   var reservation=change.getReservation();
   if(!now.isBefore(reservation.getStartAt().minusSeconds(300)))
    changes.lock(change.getId()).ifPresent(com.yachiqo.spotgo.parking.domain.model.aggregates.ReservationChange::supersede);
  }
 }
}
