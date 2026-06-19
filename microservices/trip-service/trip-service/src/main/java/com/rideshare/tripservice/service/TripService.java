package com.rideshare.tripservice.service;

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

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    public Trip createTrip(CreateTripRequest request) {
        Trip trip = new Trip();

        trip.setRiderId(request.riderId());
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

        trip.setDriverId(request.driverId());
        trip.setStatus(TripStatus.MATCHED);

        return tripRepository.save(trip);
    }

    public Trip updateTripStatus(Long tripId,
                                 UpdateTripStatusRequest request) {

        Trip trip = findTripById(tripId);

        validateTransition(
                trip.getStatus(),
                request.status()
        );

        trip.setStatus(request.status());

        return tripRepository.save(trip);
    }

    private Trip findTripById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() ->
                        new TripNotFoundException(id));
    }

    private void validateTransition(
            TripStatus current,
            TripStatus next) {

        switch (current) {

            case REQUESTED -> {
                if (next != TripStatus.MATCHED &&
                        next != TripStatus.CANCELLED) {
                    throw new IllegalStateException(
                            "Invalid transition");
                }
            }

            case MATCHED -> {
                if (next != TripStatus.IN_PROGRESS &&
                        next != TripStatus.CANCELLED) {
                    throw new IllegalStateException(
                            "Invalid transition");
                }
            }

            case IN_PROGRESS -> {
                if (next != TripStatus.COMPLETED) {
                    throw new IllegalStateException(
                            "Invalid transition");
                }
            }

            case COMPLETED, CANCELLED ->
                    throw new IllegalStateException(
                            "Trip is already finished");
        }
    }
}