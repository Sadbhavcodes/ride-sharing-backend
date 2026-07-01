package com.rideshare.locationservice.dto;

public record UpdateDriverLocationRequest(
        Long driverId,
        Double longitude,
        Double latitude
) {
}
