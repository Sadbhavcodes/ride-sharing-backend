package com.rideshare.paymentservice.repository;

import com.rideshare.paymentservice.entity.Payment;
import com.rideshare.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository
        <Payment, Long> {

    Optional<Payment> findByTripId(Long tripId);
    Optional<Payment> findByStatus(PaymentStatus status);
    boolean existsByTripId(Long tripId);
    List<Payment> findByRiderId(Long riderId);
}
