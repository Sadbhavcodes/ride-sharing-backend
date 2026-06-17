package com.rideshare.driverservice.service;

import com.rideshare.driverservice.dto.AddVehicleRequest;
import com.rideshare.driverservice.dto.UpdateVehicleVerificationStatusRequest;
import com.rideshare.driverservice.entity.Vehicle;
import com.rideshare.driverservice.exception.VehicleAlreadyExistsException;
import com.rideshare.driverservice.exception.VehicleNotFoundException;
import com.rideshare.driverservice.repository.VehicleRepository;
import org.springframework.stereotype.Service;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public Vehicle addVehicle(AddVehicleRequest request){
        if(vehicleRepository.findByPlateNumber(request.plateNumber()).isPresent()){
            throw new VehicleAlreadyExistsException("Vehicle with plate "+ request.plateNumber() + " already exists");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(request.plateNumber());
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setColor(request.color());

        return vehicleRepository.save(vehicle);
    }
    public Vehicle getVehicle(Long id){
        return findVehicleById(id);
    }

    public Vehicle updateVehicleVerificationStatus(UpdateVehicleVerificationStatusRequest request){
        Vehicle vehicle = findVehicleById(request.id());
        vehicle.setVerificationStatus(request.verificationStatus());
        return vehicleRepository.save(vehicle);
    }

    private Vehicle findVehicleById(Long id){
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException(id));
    }
}