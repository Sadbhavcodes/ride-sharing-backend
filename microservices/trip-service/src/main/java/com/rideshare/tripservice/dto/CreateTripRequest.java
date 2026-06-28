package com.rideshare.tripservice.dto;

public record CreateTripRequest(
        Long riderId,
        String pickUpLocation,
        String dropLocation
) {
}
