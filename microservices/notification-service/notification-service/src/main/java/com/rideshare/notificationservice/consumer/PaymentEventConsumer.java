package com.rideshare.notificationservice.consumer;

import com.rideshare.notificationservice.config.RabbitMQConfig;
import com.rideshare.notificationservice.event.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventConsumer {

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    public void consumePaymentCompletedEvent(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent for paymentId: {}, tripId: {}", event.id(), event.tripId());
        
        // TODO: Implement notification logic (e.g., send SMS/email to rider)
        log.info("Sending payment receipt notification to riderId: {} for amount: {}", event.riderId(), event.amount());
    }
}
