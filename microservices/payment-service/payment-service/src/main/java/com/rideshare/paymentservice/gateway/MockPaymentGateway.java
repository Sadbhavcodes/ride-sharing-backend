package com.rideshare.paymentservice.gateway;

import com.rideshare.paymentservice.dto.GatewayResponse;
import com.rideshare.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class MockPaymentGateway implements PaymentGateway {

    private static final int MAX_CACHE_SIZE = 1_000;

    private final Map<String, GatewayResponse> processedKeys =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, GatewayResponse> eldest) {
                            return size() > MAX_CACHE_SIZE;
                        }
                    }
            );

    @Override
    public GatewayResponse chargeRider(String idempotencyKey, BigDecimal amount, Long riderId) {
        synchronized (processedKeys) {
            if (processedKeys.containsKey(idempotencyKey)) {
                return processedKeys.get(idempotencyKey);
            }

            try {
                Thread.sleep(200); // simulate gateway latency
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 90 % success rate mock
            boolean success = Math.random() < 0.9;
            if (success) {
                GatewayResponse response = new GatewayResponse(
                        "MOCK-TXN-" + System.currentTimeMillis(),
                        PaymentStatus.COMPLETED
                );
                // Only cache successful payments so failed ones can be retried
                processedKeys.put(idempotencyKey, response);
                return response;
            } else {
                return new GatewayResponse(
                        "MOCK-TXN-FAILED-" + System.currentTimeMillis(),
                        PaymentStatus.FAILED
                );
            }
        }
    }
}
