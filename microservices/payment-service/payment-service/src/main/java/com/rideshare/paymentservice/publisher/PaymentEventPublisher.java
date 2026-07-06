package com.rideshare.paymentservice.publisher;

import com.rideshare.paymentservice.config.RabbitMQConfig;
import com.rideshare.paymentservice.dto.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event){
        log.info("Publishing PaymentCompletedEvent for paymentId: {}", event.id());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE,
                RabbitMQConfig.PAYMENT_COMPLETED_ROUTING_KEY,
                event
        );
        log.info("Successfully published PaymentCompletedEvent");
    }
}
