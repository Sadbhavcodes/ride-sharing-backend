package com.rideshare.tripservice.service;

import com.rideshare.tripservice.client.DriverAvailability;
import com.rideshare.tripservice.client.DriverAvailabilityDtoResponse;
import com.rideshare.tripservice.client.DriverFeignClient;
import com.rideshare.tripservice.client.MatchRequest;
import com.rideshare.tripservice.client.MatchResponse;
import com.rideshare.tripservice.client.MatchingFeignClient;
import com.rideshare.tripservice.client.UpdateDriverAvailabilityRequest;
import com.rideshare.tripservice.client.UserDto;
import com.rideshare.tripservice.client.UserFeignClient;
import com.rideshare.tripservice.dto.*;
import com.rideshare.tripservice.entity.Trip;
import com.rideshare.tripservice.entity.TripStatus;
import com.rideshare.tripservice.exception.TripNotFoundException;
import com.rideshare.tripservice.publisher.TripEventPublisher;
import com.rideshare.tripservice.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final UserFeignClient userFeignClient;
    private final DriverFeignClient driverFeignClient;
    private final MatchingFeignClient matchingFeignClient;
    private final TripEventPublisher tripEventPublisher;

    public TripService(TripRepository tripRepository,
                       UserFeignClient userFeignClient,
                       DriverFeignClient driverFeignClient,
                       MatchingFeignClient matchingFeignClient,
                       TripEventPublisher tripEventPublisher) {
        this.tripRepository = tripRepository;
        this.userFeignClient = userFeignClient;
        this.driverFeignClient = driverFeignClient;
        this.matchingFeignClient = matchingFeignClient;
        this.tripEventPublisher = tripEventPublisher;
    }

    public Trip createTrip(CreateTripRequest request) {
        // Validate rider exists in user-service
        UserDto rider = userFeignClient.getUserById(request.riderId());

        Trip trip = new Trip();
        trip.setRiderId(rider.getId());
        trip.setPickupLatitude(request.pickup().latitude());
        trip.setPickupLongitude(request.pickup().longitude());
        trip.setDestinationLatitude(request.destination().latitude());
        trip.setDestinationLongitude(request.destination().longitude());
        trip.setStatus(TripStatus.REQUESTED);

        Trip savedTrip = tripRepository.save(trip);

        // Request matching-service to find a nearby driver
        MatchRequest matchRequest = new MatchRequest(
                savedTrip.getId(),
                savedTrip.getRiderId(),
                request.pickup(),
                request.destination()
        );
        
        try {
            MatchResponse response = matchingFeignClient.findMatch(matchRequest);
            savedTrip.setDriverId(response.driverId());
            savedTrip.setStatus(TripStatus.MATCHED);
            // Persist the matched driverId FIRST, then publish event
            Trip matchedTrip = tripRepository.save(savedTrip);
            tripEventPublisher.publishTripMatched(
                    matchedTrip.getId(),
                    matchedTrip.getDriverId(),
                    matchedTrip.getRiderId()
            );
            return matchedTrip;
        } catch (Exception e) {
            // Scenario 4: Matching failed (e.g. timeout / no drivers available)
            savedTrip.setStatus(TripStatus.CANCELLED);
            Trip cancelledTrip = tripRepository.save(savedTrip);
            // driverId is null here — no driver was ever assigned
            tripEventPublisher.publishTripCancelled(
                    cancelledTrip.getId(),
                    null,
                    cancelledTrip.getRiderId()
            );
            return cancelledTrip;
        }
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

        // Persist driverId to DB FIRST, then publish event
        Trip savedTrip = tripRepository.save(trip);
        tripEventPublisher.publishTripMatched(
                savedTrip.getId(),
                savedTrip.getDriverId(),
                savedTrip.getRiderId()
        );
        return savedTrip;
    }

    public Trip updateTripStatus(Long tripId, UpdateTripStatusRequest request) {
        Trip trip = findTripById(tripId);

        validateTransition(trip.getStatus(), request.status());

        trip.setStatus(request.status());

        // When trip ends, free the driver back to ONLINE
        if (request.status() == TripStatus.COMPLETED ||
                request.status() == TripStatus.CANCELLED) {

            if (trip.getDriverId() != null) {
                driverFeignClient.releaseDriver(trip.getDriverId());
            }
        }

        // Persist status FIRST, then publish the corresponding event
        Trip savedTrip = tripRepository.save(trip);

        if (savedTrip.getStatus() == TripStatus.COMPLETED) {
            tripEventPublisher.publishTripCompleted(
                    savedTrip.getId(),
                    savedTrip.getDriverId(),
                    savedTrip.getRiderId()
            );
        } else if (savedTrip.getStatus() == TripStatus.CANCELLED) {
            tripEventPublisher.publishTripCancelled(
                    savedTrip.getId(),
                    savedTrip.getDriverId(),
                    savedTrip.getRiderId()
            );
        }

        return savedTrip;
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
                if (next != TripStatus.COMPLETED &&
                        next != TripStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid transition");
                }
            }

            case COMPLETED, CANCELLED ->
                    throw new IllegalStateException("Trip is already finished");
        }
    }
    
    public CancelTripResponse cancelTrip(Long id){
        Trip trip = findTripById(id);

        validateTransition(trip.getStatus(), TripStatus.CANCELLED);

        if(trip.getStatus().equals(TripStatus.REQUESTED)){
            // Scenario 1: No driver assigned yet
            trip.setStatus(TripStatus.CANCELLED);
        } else if(trip.getStatus().equals(TripStatus.MATCHED) || trip.getStatus().equals(TripStatus.IN_PROGRESS)){
            // Scenario 2: Driver is already assigned, we must release them first
            if (trip.getDriverId() != null) {
                driverFeignClient.releaseDriver(trip.getDriverId());
            }
            trip.setStatus(TripStatus.CANCELLED);
        }

        // Persist status FIRST, then publish event
        Trip savedTrip = tripRepository.save(trip);
        tripEventPublisher.publishTripCancelled(
                savedTrip.getId(),
                savedTrip.getDriverId(),   // null if no driver was assigned
                savedTrip.getRiderId()
        );
        return new CancelTripResponse(savedTrip.getId());
    }
}