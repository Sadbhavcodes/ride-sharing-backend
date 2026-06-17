package com.rideshare.driverservice.dto;

import com.rideshare.driverservice.entity.Availability;

public record UpdateDriverAvailabilityRequest(
        Long id,
        Availability availability
) {
}
