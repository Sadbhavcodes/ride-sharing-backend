package com.rideshare.tripservice.client;

import lombok.Data;

@Data
public class DriverAvailabilityDtoResponse {
    private Long driverId;
    private DriverAvailability availability;
}
