package com.rideshare.tripservice.repository;

import com.rideshare.tripservice.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TripRepository
        extends JpaRepository<Trip, Long> {

    List<Trip> findAllByDriverId(Long driverId);

    List<Trip> findAllByRiderId(Long riderId);
}
