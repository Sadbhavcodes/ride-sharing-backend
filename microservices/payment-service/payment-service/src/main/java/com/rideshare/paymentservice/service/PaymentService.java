package com.rideshare.paymentservice.service;

import com.rideshare.paymentservice.dto.PaymentResponse;
import com.rideshare.paymentservice.dto.TripCompletedEvent;
import com.rideshare.paymentservice.entity.Payment;
import com.rideshare.paymentservice.entity.PaymentStatus;
import com.rideshare.paymentservice.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final TripCompletedEvent event;
    public PaymentService(PaymentRepository paymentRepository, TripCompletedEvent event) {
        this.paymentRepository = paymentRepository;
        this.event = event;
    }

    public PaymentResponse getPaymentByTripId(Long tripId) {
        Payment payment = paymentRepository.findByTripId(tripId)
                .orElseThrow(() -> new RuntimeException("Payment does not exist"));
        return toResponse(payment);
    }

    public PaymentResponse getPaymentByPaymentId(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment does not exist"));
        return toResponse(payment);
    }

    public List<PaymentResponse> getPaymentsByRiderId(Long riderId) {
        return paymentRepository.findByRiderId(riderId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    public void processPayment(TripCompletedEvent event){
        Payment payment = createPendingPayment(event);


    }

    @Transactional
    private Payment createPendingPayment(TripCompletedEvent event){
        if(paymentRepository.existsByTripId(event.tripId())){
            throw new RuntimeException(
                    "Payment already exists"
            );
        }
        Payment payment = new Payment();
        payment.setTripId(event.tripId());
        payment.setRiderId(event.riderId());
        payment.setAmount(calculateFair(event));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey("trip-" + event.tripId());
        return paymentRepository.save(payment);
    }
    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getTripId(),
                p.getRiderId(),
                p.getAmount(),
                p.getStatus(),
                p.getTransactionId(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
    private BigDecimal calculateFair(TripCompletedEvent event){
        BigDecimal baseFair = new BigDecimal("30.00");
        BigDecimal pricePerKm = new BigDecimal("12.00");
        BigDecimal pricePerMinute = new BigDecimal("1.00");

        Long durationMinutes = Duration.between(
                event.startTime(),
                event.endTime()
        ).toMinutes();

        BigDecimal distanceCharge = pricePerKm.multiply(
                BigDecimal.valueOf(event.distanceKm())
        );

        BigDecimal timeCharge = pricePerMinute.multiply(
                BigDecimal.valueOf(durationMinutes)
        );
        return baseFair.add(distanceCharge).add(timeCharge);
    }

}
