package com.rideshare.matchingservice.dto;

public record DriverDto(
        Long id,
        Long userId,
        Long vehicleId,
        String availability,
        String status,
        double rating
) {
}
