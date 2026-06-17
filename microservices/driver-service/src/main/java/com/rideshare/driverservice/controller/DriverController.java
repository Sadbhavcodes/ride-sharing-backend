package com.rideshare.driverservice.controller;

import com.rideshare.driverservice.dto.CreateDriverRequest;
import com.rideshare.driverservice.dto.UpdateDriverAvailabilityRequest;
import com.rideshare.driverservice.dto.UpdateDriverStatusRequest;
import com.rideshare.driverservice.entity.Driver;
import com.rideshare.driverservice.service.DriverService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/drivers")
public class DriverController {
    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    public Driver createDriver(@RequestBody CreateDriverRequest request){
        return driverService.createDriver(request);
    }

    @GetMapping("/{id}")
    public Driver getDriver(@PathVariable Long id){
        return driverService.getDriver(id);
    }

    @GetMapping("/users/{userId}")
    public Driver getDriverByUserId(@PathVariable Long userId){
        return driverService.getDriverByUserId(userId);
    }

    @PutMapping("/status")
    public Driver updateDriverStatus(
            @RequestBody UpdateDriverStatusRequest request
            ){
        return driverService.updateDriverStatus(request);
    }

    @PutMapping("/availability")
    public Driver updateDriverAvailability(
            @RequestBody UpdateDriverAvailabilityRequest request
            ){
        return driverService.updateDriverAvailability(request);
    }
}
