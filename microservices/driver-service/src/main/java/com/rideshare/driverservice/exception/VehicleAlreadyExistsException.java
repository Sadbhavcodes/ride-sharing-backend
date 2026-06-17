package com.rideshare.driverservice.exception;

public class VehicleAlreadyExistsException extends RuntimeException{
    public VehicleAlreadyExistsException(String message){
        super(message);
    }
}
