package com.rideshare.notificationservice.consumer;

import com.rideshare.notificationservice.config.RabbitMQConfig;
import com.rideshare.notificationservice.event.TripCancelledEvent;
import com.rideshare.notificationservice.event.TripCompletedEvent;
import com.rideshare.notificationservice.event.TripMatchedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TripEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TripEventConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.MATCHED_QUEUE)
    public void handleTripMatched(TripMatchedEvent event) {
        log.info("[NOTIFICATION] Trip matched — tripId={}, riderId={}, driverId={}, occurredAt={}",
                event.tripId(), event.riderId(), event.driverId(), event.occurredAt());
    }

    @RabbitListener(queues = RabbitMQConfig.COMPLETED_QUEUE)
    public void handleTripCompleted(TripCompletedEvent event) {
        log.info("[NOTIFICATION] Trip completed — tripId={}, riderId={}, driverId={}, occurredAt={}",
                event.tripId(), event.riderId(), event.driverId(), event.occurredAt());
    }

    @RabbitListener(queues = RabbitMQConfig.CANCELLED_QUEUE)
    public void handleTripCancelled(TripCancelledEvent event) {
        log.info("[NOTIFICATION] Trip cancelled — tripId={}, riderId={}, driverId={}, occurredAt={}",
                event.tripId(), event.riderId(), event.driverId(), event.occurredAt());
    }
}