package com.rideshare.paymentservice.dto;

import com.rideshare.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long tripId,
        Long riderId,
        BigDecimal amount,
        PaymentStatus status,
        String transactionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
