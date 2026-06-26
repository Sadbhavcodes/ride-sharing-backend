package com.rideshare.tripservice.service;

import com.rideshare.tripservice.client.DriverAvailability;
import com.rideshare.tripservice.client.DriverAvailabilityDtoResponse;
import com.rideshare.tripservice.client.DriverFeignClient;
import com.rideshare.tripservice.client.UpdateDriverAvailabilityRequest;
import com.rideshare.tripservice.client.UserDto;
import com.rideshare.tripservice.client.UserFeignClient;
import com.rideshare.tripservice.dto.AssignDriverRequest;
import com.rideshare.tripservice.dto.CreateTripRequest;
import com.rideshare.tripservice.dto.UpdateTripStatusRequest;
import com.rideshare.tripservice.entity.Trip;
import com.rideshare.tripservice.entity.TripStatus;
import com.rideshare.tripservice.exception.TripNotFoundException;
import com.rideshare.tripservice.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final UserFeignClient userFeignClient;
    private final DriverFeignClient driverFeignClient;

    public TripService(TripRepository tripRepository,
                       UserFeignClient userFeignClient,
                       DriverFeignClient driverFeignClient) {
        this.tripRepository = tripRepository;
        this.userFeignClient = userFeignClient;
        this.driverFeignClient = driverFeignClient;
    }

    public Trip createTrip(CreateTripRequest request) {
        // Validate rider exists in user-service
        UserDto rider = userFeignClient.getUserById(request.riderId());

        Trip trip = new Trip();
        trip.setRiderId(rider.getId());
        trip.setPickupLocation(request.pickUpLocation());
        trip.setDropLocation(request.dropLocation());
        trip.setStatus(TripStatus.REQUESTED);

        return tripRepository.save(trip);
    }

    public Trip getTripById(Long id) {
        return findTripById(id);
    }

    public List<Trip> getTripsByDriverId(Long driverId) {
        return tripRepository.findAllByDriverId(driverId);
    }

    public List<Trip> getTripsByRiderId(Long riderId) {
        return tripRepository.findAllByRiderId(riderId);
    }

    public Trip assignDriver(Long tripId, AssignDriverRequest request) {
        Trip trip = findTripById(tripId);

        if (trip.getStatus() != TripStatus.REQUESTED) {
            throw new IllegalStateException(
                    "Driver can only be assigned to REQUESTED trips");
        }

        if (trip.getDriverId() != null) {
            throw new IllegalStateException(
                    "Trip already has a driver assigned");
        }

        // Check driver exists and is ONLINE via driver-service
        DriverAvailabilityDtoResponse driverAvailability =
                driverFeignClient.getDriverAvailability(request.driverId());

        if (driverAvailability.getAvailability() != DriverAvailability.ONLINE) {
            throw new IllegalStateException(
                    "Driver is not available. Current status: "
                            + driverAvailability.getAvailability());
        }

        // Mark driver as BUSY in driver-service
        UpdateDriverAvailabilityRequest busyRequest =
                new UpdateDriverAvailabilityRequest();
        busyRequest.setId(request.driverId());
        busyRequest.setAvailability(DriverAvailability.BUSY);
        driverFeignClient.updateDriverAvailability(busyRequest);

        trip.setDriverId(request.driverId());
        trip.setStatus(TripStatus.MATCHED);

        return tripRepository.save(trip);
    }

    public Trip updateTripStatus(Long tripId, UpdateTripStatusRequest request) {
        Trip trip = findTripById(tripId);

        validateTransition(trip.getStatus(), request.status());

        trip.setStatus(request.status());

        // When trip ends, free the driver back to ONLINE
        if (request.status() == TripStatus.COMPLETED ||
                request.status() == TripStatus.CANCELLED) {

            if (trip.getDriverId() != null) {
                UpdateDriverAvailabilityRequest freeRequest =
                        new UpdateDriverAvailabilityRequest();
                freeRequest.setId(trip.getDriverId());
                freeRequest.setAvailability(DriverAvailability.ONLINE);
                driverFeignClient.updateDriverAvailability(freeRequest);
            }
        }

        return tripRepository.save(trip);
    }

    private Trip findTripById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() ->
                        new TripNotFoundException(id));
    }

    private void validateTransition(TripStatus current, TripStatus next) {

        switch (current) {

            case REQUESTED -> {
                if (next != TripStatus.MATCHED &&
                        next != TripStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid transition");
                }
            }

            case MATCHED -> {
                if (next != TripStatus.IN_PROGRESS &&
                        next != TripStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid transition");
                }
            }

            case IN_PROGRESS -> {
                if (next != TripStatus.COMPLETED) {
                    throw new IllegalStateException("Invalid transition");
                }
            }

            case COMPLETED, CANCELLED ->
                    throw new IllegalStateException("Trip is already finished");
        }
    }
}