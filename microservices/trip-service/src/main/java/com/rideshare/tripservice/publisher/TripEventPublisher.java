package com.rideshare.tripservice.publisher;

import com.rideshare.tripservice.config.RabbitMQConfig;
import com.rideshare.tripservice.events.TripCancelledEvent;
import com.rideshare.tripservice.events.TripCompletedEvent;
import com.rideshare.tripservice.events.TripMatchedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TripEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public TripEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTripMatched(Long tripId, Long driverId, Long riderId){
        publish(
                "trip.matched",
                new TripMatchedEvent(tripId, driverId, riderId)
        );
    }

    public void publishTripCompleted(Long tripId, Long driverId, Long riderId){
        publish(
                "trip.completed",
                new TripCompletedEvent(tripId, driverId, riderId)
        );
    }

    public void publishTripCancelled(Long tripId, Long driverId, Long riderId){
        publish(
                "trip.cancelled",
                new TripCancelledEvent(tripId, driverId, riderId)
        );
    }
    private void publish(String routingKey, Object event){
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TRIP_EVENTS_EXCHANGE,
                routingKey,
                event
        );
    }
}
