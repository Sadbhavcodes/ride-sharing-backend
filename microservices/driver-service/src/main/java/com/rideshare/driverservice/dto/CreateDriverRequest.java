package com.rideshare.driverservice.dto;

public record CreateDriverRequest(
        Long userId,
        Long vehicleId
) {
}
