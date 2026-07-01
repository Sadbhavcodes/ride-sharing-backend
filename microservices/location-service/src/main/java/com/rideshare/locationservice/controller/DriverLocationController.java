package com.rideshare.locationservice.controller;

import com.rideshare.locationservice.dto.DriverLocationResponse;
import com.rideshare.locationservice.dto.NearestDriverRequest;
import com.rideshare.locationservice.dto.UpdateDriverLocationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.rideshare.locationservice.service.DriverLocationService;

import java.util.List;

@RestController
@RequestMapping("/locations")
public class DriverLocationController {

    private final DriverLocationService driverLocationService;

    public DriverLocationController(DriverLocationService driverLocationService) {
        this.driverLocationService = driverLocationService;
    }

    @GetMapping("/drivers/{driverId}")
    public DriverLocationResponse getDriverLocation(
            @PathVariable Long driverId
    ) {
        return driverLocationService.getDriverById(driverId);
    }

    @PostMapping("/ping")
    public DriverLocationResponse updateLocation(
            @RequestBody UpdateDriverLocationRequest request
    ) {
        return driverLocationService.updateDriverLocation(request);
    }

    @PostMapping("/drivers/nearby")
    public List<DriverLocationResponse> findNearbyDrivers(
            @RequestBody NearestDriverRequest request
    ) {
        return driverLocationService.findNearbyDrivers(
                request.latitude(),
                request.longitude(),
                5000 // default 5km radius in meters
        );
    }

    @DeleteMapping("/{driverId}")
    public ResponseEntity<Void> removeDriverLocation(
            @PathVariable Long driverId
    ) {
        driverLocationService.removeDriverLocation(driverId);
        return ResponseEntity.noContent().build();
    }
}
