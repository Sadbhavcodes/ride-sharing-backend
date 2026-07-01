package com.rideshare.locationservice.repository;

import com.rideshare.locationservice.entity.DriverLocation;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverLocationRepository
        extends JpaRepository<DriverLocation, Long> {

    Optional<DriverLocation> findByDriverId(Long driverId);

    @Query(
            value = """
            SELECT *
            FROM driver_locations
            WHERE ST_DWithin(
                location,
                :point,
                :radius
            )
            ORDER BY ST_Distance(
                 location,
                 :point
            )
            LIMIT 10;
            """,
            nativeQuery = true
    )
    List<DriverLocation> findNearbyDrivers(
            @Param("point") Point point,
            @Param("radius") double radius
    );
}
