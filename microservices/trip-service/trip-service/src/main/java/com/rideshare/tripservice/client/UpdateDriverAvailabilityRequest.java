package com.rideshare.tripservice.client;

import lombok.Data;

@Data
public class UpdateDriverAvailabilityRequest {
    private Long id;
    DriverAvailability availability;
}
