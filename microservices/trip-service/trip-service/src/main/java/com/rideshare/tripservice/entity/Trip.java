package com.rideshare.tripservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@lombok.Setter
@lombok.Getter
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long riderId;
    private Long driverId;

    private String pickupLocation;
    private String dropLocation;

    @Enumerated(value = EnumType.STRING)
    TripStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
