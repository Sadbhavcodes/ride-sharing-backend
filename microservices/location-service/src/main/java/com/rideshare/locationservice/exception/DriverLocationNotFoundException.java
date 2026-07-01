package com.rideshare.locationservice.exception;

public class DriverLocationNotFoundException extends RuntimeException {
    public DriverLocationNotFoundException(Long driverId) {
        super("Location for driver with id " + driverId + " not found");
    }
}
