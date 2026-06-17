package com.rideshare.driverservice.dto;

import com.rideshare.driverservice.entity.VerificationStatus;

public record UpdateVehicleVerificationStatusRequest(
        Long id,
        VerificationStatus verificationStatus
) {
}
