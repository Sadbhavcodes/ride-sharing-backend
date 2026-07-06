package com.rideshare.tripservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long riderId;
    private Long driverId;

    private Double pickupLatitude;
    private Double pickupLongitude;

    private Double destinationLatitude;
    private Double destinationLongitude;

    @Enumerated(value = EnumType.STRING)
    private TripStatus status;

    // Set when driver starts the trip (IN_PROGRESS transition)
    private LocalDateTime tripStartTime;

    // Set when trip is completed (COMPLETED transition)
    private LocalDateTime tripEndTime;

    // Populated via location-service Feign call on COMPLETED transition
    private Double distanceKm;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
