package com.rideshare.notificationservice.event;

import java.math.BigDecimal;

public record PaymentCompletedEvent(
        Long id,
        Long tripId,
        Long riderId,
        BigDecimal amount,
        String transactionId
) {
}
