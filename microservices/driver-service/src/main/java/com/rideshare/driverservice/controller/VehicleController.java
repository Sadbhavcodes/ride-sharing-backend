package com.rideshare.driverservice.controller;

import com.rideshare.driverservice.dto.AddVehicleRequest;
import com.rideshare.driverservice.dto.UpdateVehicleVerificationStatusRequest;
import com.rideshare.driverservice.entity.Vehicle;
import com.rideshare.driverservice.service.VehicleService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vehicles")
public class VehicleController {
    private final VehicleService vehicleService;


    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public Vehicle addVehicle(@RequestBody AddVehicleRequest request){
        return vehicleService.addVehicle(request);
    }

    @GetMapping("/{id}")
    public Vehicle getVehicle(@PathVariable Long id){
        return vehicleService.getVehicle(id);
    }

    @PutMapping("/{id}")
    public Vehicle updateVehicleVerificationStatus(
            @RequestBody UpdateVehicleVerificationStatusRequest request
            ){
        return vehicleService.updateVehicleVerificationStatus(request);
    }
}
