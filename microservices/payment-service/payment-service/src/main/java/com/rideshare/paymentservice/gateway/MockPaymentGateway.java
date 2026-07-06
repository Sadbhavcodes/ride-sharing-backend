package com.rideshare.paymentservice.gateway;

import com.rideshare.paymentservice.dto.GatewayResponse;
import com.rideshare.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class MockPaymentGateway implements PaymentGateway{

    private final Map<String, GatewayResponse> processedKeys = new ConcurrentHashMap<>();

    @Override
    public GatewayResponse chargeRider(String idempotencyKey, BigDecimal amount, Long riderId) {
        return processedKeys.computeIfAbsent(idempotencyKey, key-> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }

            boolean success = Math.random() < 0.9;
            if (success){
                return new GatewayResponse(
                        "MOCK-TXN-" + System.currentTimeMillis(),
                        PaymentStatus.COMPLETED
                );
            } else{
                return new GatewayResponse(
                        "null",
                        PaymentStatus.PENDING
                );
            }
        });
    }
}
