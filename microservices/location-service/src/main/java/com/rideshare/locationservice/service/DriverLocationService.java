package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.DistanceResponse;
import com.rideshare.locationservice.dto.DriverLocationResponse;
import com.rideshare.locationservice.dto.UpdateDriverLocationRequest;
import com.rideshare.locationservice.entity.DriverLocation;
import com.rideshare.locationservice.exception.DriverLocationNotFoundException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import com.rideshare.locationservice.repository.DriverLocationRepository;

import java.util.List;

@Service
public class DriverLocationService {
    private final DriverLocationRepository driverLocationRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(
            new PrecisionModel(),4326
    );

    public DriverLocationService(DriverLocationRepository driverLocationRepository) {
        this.driverLocationRepository = driverLocationRepository;
    }

    public DriverLocationResponse getDriverById(Long driverId){
        DriverLocation driver = driverLocationRepository.findByDriverId(driverId)
                .orElseThrow(() -> new DriverLocationNotFoundException(driverId));

        Point point = driver.getLocation();

        return new DriverLocationResponse(
                driver.getDriverId(),
                point.getY(),
                point.getX()
        );
    }

    public DriverLocationResponse updateDriverLocation(UpdateDriverLocationRequest request) {

        DriverLocation driverLocation = driverLocationRepository
                .findByDriverId(request.driverId())
                .orElseGet(() -> {
                    DriverLocation newLocation = new DriverLocation();
                    newLocation.setDriverId(request.driverId());
                    return newLocation;
                });

        Coordinate coordinate = new Coordinate(
                request.longitude(),
                request.latitude()
        );
        Point point = geometryFactory.createPoint(coordinate);
        point.setSRID(4326);

        driverLocation.setLocation(point);

        driverLocationRepository.save(driverLocation);

        Point savedPoint = driverLocation.getLocation();

        return new DriverLocationResponse(
                driverLocation.getDriverId(),
                savedPoint.getY(),
                savedPoint.getX()
        );
    }

    public List<DriverLocationResponse> findNearbyDrivers(
            double latitude,
            double longitude,
            double radius
    ){
        Coordinate coordinate = new Coordinate(longitude,latitude);

        Point point = geometryFactory.createPoint(coordinate);
        point.setSRID(4326);

        List<DriverLocation> driverLocations =
                driverLocationRepository.findNearbyDrivers(point, radius);

        return driverLocations.stream()
                .map(driver -> new DriverLocationResponse(
                        driver.getDriverId(),
                        driver.getLocation().getY(),
                        driver.getLocation().getX()
                )).toList();

    }

    public void removeDriverLocation(Long driverId) {
        DriverLocation driverLocation = driverLocationRepository.findByDriverId(driverId)
                .orElseThrow(() -> new DriverLocationNotFoundException(driverId));

        driverLocationRepository.delete(driverLocation);
    }

    /**
     * Calculates the straight-line distance in kilometres between two geographic
     * coordinates using the Haversine formula.
     * <p>
     * The location-service is the single authority for all geo calculations in
     * this system — trip-service delegates here instead of doing maths itself,
     * keeping service boundaries clean.
     */
    public DistanceResponse calculateDistance(
            double fromLat, double fromLng,
            double toLat,   double toLng
    ) {
        final double EARTH_RADIUS_KM = 6371.0;

        double dLat = Math.toRadians(toLat - fromLat);
        double dLng = Math.toRadians(toLng - fromLng);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = EARTH_RADIUS_KM * c;

        // Round to 2 decimal places for clean event payloads
        double rounded = Math.round(distanceKm * 100.0) / 100.0;
        return new DistanceResponse(rounded);
    }

}
