package com.rideshare.tripservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "driverservice")
public interface DriverFeignClient {

    @GetMapping("/drivers/{id}/availability")
    DriverAvailabilityDtoResponse getDriverAvailability(@PathVariable("id") Long driverId);

    @PutMapping("/drivers/availability")
    DriverAvailabilityDtoResponse updateDriverAvailability(
            @RequestBody UpdateDriverAvailabilityRequest request);

    @PostMapping("/drivers/{id}/claim")
    DriverAvailabilityDtoResponse claimDriver(@PathVariable("id") Long driverId);

    @PostMapping("/drivers/{id}/release")
    void releaseDriver(@PathVariable("id") Long driverId);
}

