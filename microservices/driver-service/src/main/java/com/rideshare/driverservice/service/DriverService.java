package com.rideshare.driverservice.service;

import com.rideshare.driverservice.client.UserDto;
import com.rideshare.driverservice.client.UserFeignClient;
import com.rideshare.driverservice.dto.CreateDriverRequest;
import com.rideshare.driverservice.dto.DriverAvailabilityResponse;
import com.rideshare.driverservice.dto.UpdateDriverAvailabilityRequest;
import com.rideshare.driverservice.dto.UpdateDriverStatusRequest;
import com.rideshare.driverservice.entity.Availability;
import com.rideshare.driverservice.entity.Driver;
import com.rideshare.driverservice.entity.Status;
import com.rideshare.driverservice.exception.DriverAlreadyExistsException;
import com.rideshare.driverservice.exception.DriverNotFoundException;
import com.rideshare.driverservice.repository.DriverRepository;
import org.springframework.stereotype.Service;

@Service
public class DriverService {
    private final DriverRepository driverRepository;
    private final UserFeignClient userFeignClient;
    public DriverService(DriverRepository driverRepository, UserFeignClient userFeignClient) {
        this.driverRepository = driverRepository;
        this.userFeignClient = userFeignClient;
    }

    public Driver createDriver(CreateDriverRequest createDriverRequest){
        if(driverRepository.findByUserId(createDriverRequest.userId()).isPresent()){
            throw new DriverAlreadyExistsException(createDriverRequest.userId());
        }
        UserDto user = userFeignClient.getUserById(createDriverRequest.userId());
        
        if (!"DRIVER".equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("User with ID " + createDriverRequest.userId() + " does not have the DRIVER role");
        }
        
        Driver driver = new Driver();
        driver.setUserId(createDriverRequest.userId());
        driver.setVehicleId(createDriverRequest.vehicleId());
        driver.setStatus(Status.PENDING);
        driver.setAvailability(Availability.OFFLINE);

        return driverRepository.save(driver);
    }

    public Driver getDriver(Long id){
        return findDriverById(id);
    }

    public DriverAvailabilityResponse getDriverAvailability(Long id){
        Driver driver = findDriverById(id);
        return new DriverAvailabilityResponse(driver.getId(), driver.getAvailability());
    }

    public Driver getDriverByUserId(Long userId){
        return findDriverByUserId(userId);
    }

    public Driver updateDriverStatus(UpdateDriverStatusRequest request){
        Driver driver = findDriverById(request.id());
        driver.setStatus(request.status());
        return driverRepository.save(driver);
    }

    public Driver updateDriverAvailability(UpdateDriverAvailabilityRequest request){
        Driver driver = findDriverById(request.id());
        driver.setAvailability(request.availability());
        return driverRepository.save(driver);
    }

    private Driver findDriverById(Long id){
        return driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException(id));
    }
    private Driver findDriverByUserId(Long userId){
        return driverRepository.findByUserId(userId)
                .orElseThrow(() -> new DriverNotFoundException(userId));
    }
}