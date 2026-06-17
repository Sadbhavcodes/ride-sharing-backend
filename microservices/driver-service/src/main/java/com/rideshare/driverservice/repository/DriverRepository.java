package com.rideshare.driverservice.repository;

import com.rideshare.driverservice.entity.Availability;
import com.rideshare.driverservice.entity.Driver;
import com.rideshare.driverservice.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByUserId(Long id);
    List<Driver> findByStatusAndAvailability(
            Status status,
            Availability availability
    );
}
