package com.rideshare.paymentservice.dto;

import java.time.LocalDateTime;

public record TripCompletedEvent(
        Long tripId,
        Long driverId,
        Long riderId,
        Double distanceKm,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
