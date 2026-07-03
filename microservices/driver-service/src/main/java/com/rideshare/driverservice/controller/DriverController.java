package com.rideshare.driverservice.controller;

import com.rideshare.driverservice.dto.CreateDriverRequest;
import com.rideshare.driverservice.dto.DriverAvailabilityResponse;
import com.rideshare.driverservice.dto.UpdateDriverAvailabilityRequest;
import com.rideshare.driverservice.dto.UpdateDriverStatusRequest;
import com.rideshare.driverservice.entity.Driver;
import com.rideshare.driverservice.service.DriverService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/{id}/availability")
    public DriverAvailabilityResponse getDriverAvailability(@PathVariable Long id){
        return driverService.getDriverAvailability(id);
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

    @PostMapping("/available")
    public List<Driver> getAvailableDrivers(@RequestBody List<Long> driverIds) {
        return driverService.getAvailableDriversByIds(driverIds);
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<Driver> claimDriver(@PathVariable Long id) {
        try {
            Driver driver = driverService.claimDriver(id);
            return ResponseEntity.ok(driver);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Another request already claimed this driver — version mismatch
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{id}/release")
    public Driver releaseDriver(@PathVariable Long id) {
        return driverService.releaseDriver(id);
    }
}
