package com.rideshare.driverservice.exception;

public class DriverAlreadyExistsException extends RuntimeException{
    public DriverAlreadyExistsException(Long id){
        super("Driver with id " + id + " already exists");
    }
}
