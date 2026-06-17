package com.rideshare.driverservice.dto;

import com.rideshare.driverservice.entity.Status;

public record UpdateDriverStatusRequest(
        Long id,
        Status status
) {
}
