package com.rideshare.locationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "driver_locations")
public class DriverLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long driverId;

    @Column(columnDefinition = "geography(Point, 4326)")
    private Point location;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
