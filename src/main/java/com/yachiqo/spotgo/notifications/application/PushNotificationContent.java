package com.yachiqo.spotgo.notifications.application;

import com.yachiqo.spotgo.shared.domain.events.SpotgoDomainEvent;

public record PushNotificationContent(String title, String body) {
    public static PushNotificationContent from(SpotgoDomainEvent event) {
        return switch (event.type()) {
            case "parking.reservation.confirmed" -> new PushNotificationContent(
                    "Reservation confirmed", "Your parking reservation is confirmed.");
            case "parking.reservation.cancelled" -> new PushNotificationContent(
                    "Reservation cancelled", "Your parking reservation was cancelled.");
            case "parking.reservation.reassigned" -> new PushNotificationContent(
                    "Reservation moved", "Your parking spot was changed because of an occupancy incident. Review the updated zone and spot in the app.");
            case "parking.session.completed" -> new PushNotificationContent(
                    "Parking session completed", "Your parking session has ended.");
            case "billing.payment.approved" -> new PushNotificationContent(
                    "Payment approved", "Your SpotGo payment was approved.");
            case "billing.payment.rejected" -> new PushNotificationContent(
                    "Payment not approved", "Your SpotGo payment could not be approved. Review the payment status in the app.");
            case "billing.subscription.started" -> new PushNotificationContent(
                    "Subscription active", "Your SpotGo subscription is active.");
            case "billing.subscription.adjusted" -> new PushNotificationContent(
                    "Subscription updated", "Your tenant subscription was updated after the parking spot change.");
            case "monitoring.alert.opened" -> new PushNotificationContent(
                    "Parking alert", "A parking or sensor incident needs your attention.");
            case "monitoring.capacity.high" -> new PushNotificationContent(
                    "Critical parking capacity", "Occupancy is above 95% of operational parking spots.");
            default -> null;
        };
    }
}
