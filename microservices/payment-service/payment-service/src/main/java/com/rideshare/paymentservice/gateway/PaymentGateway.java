package com.rideshare.paymentservice.gateway;

import com.rideshare.paymentservice.dto.GatewayResponse;

import java.math.BigDecimal;

public interface PaymentGateway {
    GatewayResponse chargeRider(String idempotencyKey, BigDecimal amount, Long riderId);
}
