package service;

import dto.DriverLocationResponse;
import dto.UpdateDriverLocationRequest;
import entity.DriverLocation;
import org.springframework.stereotype.Service;
import repository.DriverLocationRepository;

import java.util.Optional;

@Service
public class DriverLocationService {
    private final DriverLocationRepository driverLocationRepository;

    public DriverLocationService(DriverLocationRepository driverLocationRepository) {
        this.driverLocationRepository = driverLocationRepository;
    }

    public DriverLocationResponse getDriverById(Long driverId){
        Optional<DriverLocation> driverLocation = driverLocationRepository.findByDriverId(driverId);

        if(driverLocation.isEmpty()){
            throw new RuntimeException(
                    "Driver is not available at the moment !"
            );
        }
        DriverLocation driver = driverLocation.get();
        return new DriverLocationResponse(
                driver.getDriverId(),
                driver.getLongitude(),
                driver.getLatitude()
        );
    }

    public DriverLocationResponse updateDriverLocation(UpdateDriverLocationRequest request) {

        Optional<DriverLocation> optionalDriverLocation =
                driverLocationRepository.findByDriverId(request.driverId());

        DriverLocation driverLocation;

        if (optionalDriverLocation.isPresent()) {
            driverLocation = optionalDriverLocation.get();
        } else {
            driverLocation = new DriverLocation();
            driverLocation.setDriverId(request.driverId());
        }

        driverLocation.setLatitude(request.latitude());
        driverLocation.setLongitude(request.longitude());

        driverLocationRepository.save(driverLocation);

        return new DriverLocationResponse(
                driverLocation.getDriverId(),
                driverLocation.getLatitude(),
                driverLocation.getLongitude()
        );
    }

}
