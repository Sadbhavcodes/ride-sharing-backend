package com.rideshare.tripservice.dto;

import com.rideshare.tripservice.entity.TripStatus;

public record UpdateTripStatusRequest(
        TripStatus status
) {
}
