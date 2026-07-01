package com.rideshare.locationservice.dto;

public record DriverLocationResponse(
        Long driverId,
        Double longitude,
        Double latitude
) {
}
