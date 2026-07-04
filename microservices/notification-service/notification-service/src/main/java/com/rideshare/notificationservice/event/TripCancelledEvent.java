package com.rideshare.notificationservice.event;

import java.time.LocalDateTime;

public record TripCancelledEvent(
        Long tripId,
        Long riderId,
        Long driverId,
        LocalDateTime occurredAt
) {
}
