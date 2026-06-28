package com.rideshare.tripservice.exception;

public class RiderNotFoundException extends RuntimeException {

    public RiderNotFoundException(Long riderId) {
        super("Rider not found with id: " + riderId);
    }
}
