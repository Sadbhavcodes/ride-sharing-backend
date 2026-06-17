package com.rideshare.driverservice.entity;

import jakarta.persistence.*;

@Entity
@lombok.Setter
@lombok.Getter
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long userId;

    @Column(unique = true)
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    private Availability availability;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false)
    private double rating = 0.0;
}