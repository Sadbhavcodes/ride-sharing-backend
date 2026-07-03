package com.rideshare.matchingservice.dto;

public record DriverLocationResponse(
        Long driverId,
        Double longitude,
        Double latitude
) {
}
