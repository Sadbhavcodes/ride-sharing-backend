package com.rideshare.paymentservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String TRIP_EVENTS_EXCHANGE    = "trip.events";
    public static final String DEAD_LETTER_EXCHANGE    = "trip.events.dlx";

    public static final String PAYMENT_TRIP_COMPLETED_QUEUE = "payment.trip.completed";
    public static final String DEAD_LETTER_QUEUE            = "trip.events.dead-letter";

    public static final String TRIP_COMPLETED_ROUTING_KEY    = "trip.completed";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";


    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange tripEventsExchange() {
        return new TopicExchange(TRIP_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue paymentTripCompletedQueue() {
        return QueueBuilder.durable(PAYMENT_TRIP_COMPLETED_QUEUE)
                .withArgument("x-dead-letter-exchange",    DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "#")
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding paymentTripCompletedBinding() {
        return BindingBuilder
                .bind(paymentTripCompletedQueue())
                .to(tripEventsExchange())
                .with(TRIP_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(
            Queue deadLetterQueue,
            TopicExchange deadLetterExchange
    ) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with("#");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        return factory;
    }
}