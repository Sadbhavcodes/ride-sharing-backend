package controller;

import dto.DriverLocationResponse;
import dto.UpdateDriverLocationRequest;
import org.springframework.web.bind.annotation.*;
import service.DriverLocationService;

@RestController
@RequestMapping ("/locations")
public class DriverLocationController {

    private final DriverLocationService driverLocationService;

    public DriverLocationController(DriverLocationService driverLocationService) {
        this.driverLocationService = driverLocationService;
    }

    @GetMapping("/drivers/{id}")
    public DriverLocationResponse getDriverLocation(
            @PathVariable Long driverId
    ){
        return driverLocationService.getDriverById(driverId);
    }

    @PostMapping("/ping")
    public DriverLocationResponse updateLocation(
            @RequestBody UpdateDriverLocationRequest request
            ){
        return driverLocationService.updateDriverLocation(request);
    }
}
