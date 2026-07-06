package com.rideshare.paymentservice.dto;

import com.rideshare.paymentservice.entity.PaymentStatus;


public record GatewayResponse(
        String transactionId,
        PaymentStatus status
) {
}
