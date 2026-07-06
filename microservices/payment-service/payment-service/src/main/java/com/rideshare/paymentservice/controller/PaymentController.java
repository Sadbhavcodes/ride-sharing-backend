package com.rideshare.paymentservice.controller;

import com.rideshare.paymentservice.dto.PaymentResponse;
import com.rideshare.paymentservice.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/trips/{tripId}")
    public ResponseEntity<PaymentResponse> getPaymentByTripId(@PathVariable Long tripId) {
        return ResponseEntity.ok(paymentService.getPaymentByTripId(tripId));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentByPaymentId(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentByPaymentId(paymentId));
    }

    @GetMapping("/riders/{riderId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByRiderId(@PathVariable Long riderId) {
        return ResponseEntity.ok(paymentService.getPaymentsByRiderId(riderId));
    }
}
