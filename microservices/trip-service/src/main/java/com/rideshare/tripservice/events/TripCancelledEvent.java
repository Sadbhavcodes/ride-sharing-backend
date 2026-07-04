package com.rideshare.tripservice.events;

import java.time.LocalDateTime;

public record TripCancelledEvent(
        Long tripId,
        Long driverId,
        Long riderId,
        LocalDateTime occurredAt
) {
}

