package com.rideshare.driverservice.dto;

public record AddVehicleRequest(
        String plateNumber,
        String make,
        String model,
        String color
) {
}
