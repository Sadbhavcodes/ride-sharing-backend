package com.rideshare.driverservice.exception;

public class DriverNotFoundException extends RuntimeException{
    public DriverNotFoundException(Long id) {
        super("Driver with id " + id + " not found");
    }
}
