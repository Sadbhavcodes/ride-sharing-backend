package com.rideshare.tripservice.events;

import java.time.LocalDateTime;

/**
 * Published when a trip transitions to COMPLETED.
 *
 * Carries the raw data the payment-service needs to:
 *  - Calculate the fare (distanceKm + duration from start/end time)
 *  - Record the transaction
 *
 * distanceKm is computed by location-service (Haversine) and forwarded here —
 * payment-service must NOT recalculate it, and trip-service must NOT compute
 * fares. Each service owns exactly one concern.
 */
public record TripCompletedEvent(
        Long tripId,
        Long driverId,
        Long riderId,
        Double distanceKm,
        LocalDateTime tripStartTime,
        LocalDateTime tripEndTime
) {
}
