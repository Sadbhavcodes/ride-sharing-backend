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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public List<Driver> getAvailableDriversByIds(List<Long> driverIds) {
        return driverRepository.findByIdInAndStatusAndAvailability(
                driverIds, Status.ACTIVE, Availability.ONLINE
        );
    }

    /**
     * Atomically claim a driver for a trip: read + check ONLINE + set BUSY
     * all in one transaction. If another thread claimed this driver between
     * our read and write, @Version causes OptimisticLockException.
     */
    @Transactional
    public Driver claimDriver(Long driverId) {
        Driver driver = findDriverById(driverId);

        if (driver.getAvailability() != Availability.ONLINE) {
            throw new IllegalStateException(
                    "Driver " + driverId + " is not ONLINE, current: " + driver.getAvailability());
        }

        driver.setAvailability(Availability.BUSY);
        return driverRepository.save(driver);
        // If version changed since our read → OptimisticLockException thrown here
    }

    /**
     * Release a driver back to ONLINE (used on trip cancel/complete).
     */
    @Transactional
    public Driver releaseDriver(Long driverId) {
        Driver driver = findDriverById(driverId);
        driver.setAvailability(Availability.ONLINE);
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