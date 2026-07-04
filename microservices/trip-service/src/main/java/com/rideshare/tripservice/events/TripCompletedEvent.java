package com.rideshare.tripservice.events;

public record TripCompletedEvent(
        Long tripId,
        Long driverId,
        Long riderId
) {
}
