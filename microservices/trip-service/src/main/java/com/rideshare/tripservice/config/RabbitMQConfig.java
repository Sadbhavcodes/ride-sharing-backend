package com.rideshare.tripservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String TRIP_EVENTS_EXCHANGE = "trip.events";

    @Bean
    public TopicExchange tripEventsExchange(){
        return new TopicExchange(TRIP_EVENTS_EXCHANGE);
    }
}
