package com.rideshare.driverservice.dto;

import com.rideshare.driverservice.entity.Availability;

public record DriverAvailabilityResponse(
        Long driverId,
        Availability availability
) {
}
