package com.rideshare.tripservice.service;

import com.rideshare.tripservice.client.DistanceRequest;
import com.rideshare.tripservice.client.DistanceResponse;
import com.rideshare.tripservice.client.DriverFeignClient;
import com.rideshare.tripservice.client.LocationFeignClient;
import com.rideshare.tripservice.client.MatchRequest;
import com.rideshare.tripservice.client.MatchResponse;
import com.rideshare.tripservice.client.MatchingFeignClient;
import com.rideshare.tripservice.client.UserDto;
import com.rideshare.tripservice.client.UserFeignClient;
import com.rideshare.tripservice.dto.*;
import com.rideshare.tripservice.entity.Trip;
import com.rideshare.tripservice.entity.TripStatus;
import com.rideshare.tripservice.exception.TripNotFoundException;
import com.rideshare.tripservice.publisher.TripEventPublisher;
import com.rideshare.tripservice.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final UserFeignClient userFeignClient;
    private final DriverFeignClient driverFeignClient;
    private final MatchingFeignClient matchingFeignClient;
    private final LocationFeignClient locationFeignClient;
    private final TripEventPublisher tripEventPublisher;

    public TripService(TripRepository tripRepository,
                       UserFeignClient userFeignClient,
                       DriverFeignClient driverFeignClient,
                       MatchingFeignClient matchingFeignClient,
                       LocationFeignClient locationFeignClient,
                       TripEventPublisher tripEventPublisher) {
        this.tripRepository = tripRepository;
        this.userFeignClient = userFeignClient;
        this.driverFeignClient = driverFeignClient;
        this.matchingFeignClient = matchingFeignClient;
        this.locationFeignClient = locationFeignClient;
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

        // Atomically claim the driver (checks ONLINE + sets BUSY in one transaction).
        // If another request grabbed this driver first, driver-service returns 409 → FeignException.Conflict.
        try {
            driverFeignClient.claimDriver(request.driverId());
        } catch (feign.FeignException.Conflict e) {
            throw new IllegalStateException(
                    "Driver " + request.driverId() + " was just claimed by another request");
        } catch (feign.FeignException.UnprocessableEntity | feign.FeignException.BadRequest e) {
            throw new IllegalStateException(
                    "Driver " + request.driverId() + " is not available for assignment");
        }

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

        // Capture trip start time when driver begins the ride
        if (request.status() == TripStatus.IN_PROGRESS) {
            trip.setTripStartTime(LocalDateTime.now());
        }

        // When trip ends, free the driver back to ONLINE
        if (request.status() == TripStatus.COMPLETED ||
                request.status() == TripStatus.CANCELLED) {

            if (trip.getDriverId() != null) {
                driverFeignClient.releaseDriver(trip.getDriverId());
            }
        }

        // On COMPLETED: fetch distance from location-service and capture end time
        if (request.status() == TripStatus.COMPLETED) {
            trip.setTripEndTime(LocalDateTime.now());

            Double distanceKm = fetchDistanceKm(trip);
            trip.setDistanceKm(distanceKm);
        }

        // Persist status (+ new timestamps / distance) FIRST, then publish
        Trip savedTrip = tripRepository.save(trip);

        if (savedTrip.getStatus() == TripStatus.COMPLETED) {
            tripEventPublisher.publishTripCompleted(
                    savedTrip.getId(),
                    savedTrip.getDriverId(),
                    savedTrip.getRiderId(),
                    savedTrip.getDistanceKm(),
                    savedTrip.getTripStartTime(),
                    savedTrip.getTripEndTime()
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

    /**
     * Calls location-service to compute the Haversine distance between the trip's
     * pickup and destination. If the call fails (e.g. location-service is down),
     * we fall back to null so the trip can still complete — payment-service should
     * handle a null distanceKm gracefully (e.g. flag for manual review).
     */
    private Double fetchDistanceKm(Trip trip) {
        try {
            DistanceResponse response = locationFeignClient.calculateDistance(
                    new DistanceRequest(
                            trip.getPickupLatitude(),
                            trip.getPickupLongitude(),
                            trip.getDestinationLatitude(),
                            trip.getDestinationLongitude()
                    )
            );
            return response.distanceKm();
        } catch (Exception e) {
            log.error("Could not fetch distance from location-service for trip {}.", trip.getId(), e);
            throw new IllegalStateException("Location service is unavailable. Cannot calculate fare. Please try again.");
        }
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