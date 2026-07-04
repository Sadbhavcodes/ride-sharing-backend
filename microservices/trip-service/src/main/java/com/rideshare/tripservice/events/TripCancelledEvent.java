package com.rideshare.tripservice.events;

public record TripCancelledEvent(
        Long tripId,
        Long driverId,
        Long riderId
) {
}
