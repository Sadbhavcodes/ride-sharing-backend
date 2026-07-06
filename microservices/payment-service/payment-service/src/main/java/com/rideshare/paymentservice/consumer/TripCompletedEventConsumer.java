package com.rideshare.paymentservice.consumer;

import com.rideshare.paymentservice.config.RabbitMQConfig;
import com.rideshare.paymentservice.dto.TripCompletedEvent;
import com.rideshare.paymentservice.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TripCompletedEventConsumer {
    private final PaymentService paymentService;

    public TripCompletedEventConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_TRIP_COMPLETED_QUEUE)
    public void consumeTripCompletedEvent(TripCompletedEvent event){
        log.info("Received trip completed event for tripId: {}",event.tripId());
        try {
            paymentService.processPayment(event);
            log.info("Successfully processed payment for tripId: {}", event.tripId());
        } catch (Exception e){
            throw e;
        }
    }
}
