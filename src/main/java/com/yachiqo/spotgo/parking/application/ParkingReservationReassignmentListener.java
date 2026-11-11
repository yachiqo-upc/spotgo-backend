package com.yachiqo.spotgo.parking.application;

import com.yachiqo.spotgo.shared.domain.events.SpotgoDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ParkingReservationReassignmentListener {
    private static final Logger log = LoggerFactory.getLogger(ParkingReservationReassignmentListener.class);
    private final ParkingReservationReassignmentService reassignment;

    public ParkingReservationReassignmentListener(ParkingReservationReassignmentService reassignment) {
        this.reassignment = reassignment;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SpotgoDomainEvent event) {
        if (!"monitoring.alert.opened".equals(event.type()) || !event.data().containsKey("conflict_id")) return;
        try {
            reassignment.reassignFor(event);
        } catch (RuntimeException exception) {
            log.error("Reservation reassignment failed after monitoring conflict {}.",
                    event.data().get("conflict_id"), exception);
        }
    }
}
