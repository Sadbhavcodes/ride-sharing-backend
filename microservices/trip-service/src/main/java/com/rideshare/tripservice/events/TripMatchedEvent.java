package com.rideshare.tripservice.events;

import java.time.LocalDateTime;

public record TripMatchedEvent(
        Long tripId,
        Long driverId,
        Long riderId,
        LocalDateTime occurredAt
) {
}

