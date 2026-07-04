package com.rideshare.tripservice.events;

public record TripMatchedEvent(
        Long tripId,
        Long driverId,
        Long riderId
) {
}
