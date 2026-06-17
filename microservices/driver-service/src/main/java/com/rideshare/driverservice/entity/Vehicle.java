package com.rideshare.driverservice.entity;

import jakarta.persistence.*;

@Entity
@lombok.Getter
@lombok.Setter
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String plateNumber;

    private String make;
    private String model;
    private String color;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;
}
