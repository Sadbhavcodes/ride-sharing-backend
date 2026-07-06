package com.rideshare.paymentservice.dto;

import java.math.BigDecimal;

public record PaymentCompletedEvent(
        Long id,
        Long tripId,
        Long riderId,
        BigDecimal amount,
        String transactionId
) {
}
