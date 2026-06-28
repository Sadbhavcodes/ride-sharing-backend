package com.rideshare.tripservice.exception;

public class TripNotFoundException extends RuntimeException{
    public TripNotFoundException(Long id){
        super("Trip with id "+ id + " not found");
    }
}
