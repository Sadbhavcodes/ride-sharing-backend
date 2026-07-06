package com.rideshare.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String TRIP_EVENT_EXCHANGE = "trip.events";
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String DEAD_LETTER_EXCHANGE = "trip.events.dlx";

    public static final String MATCHED_QUEUE = "notification.trip.matched";
    public static final String COMPLETED_QUEUE = "notification.trip.completed";
    public static final String CANCELLED_QUEUE = "notification.trip.cancelled";
    public static final String PAYMENT_COMPLETED_QUEUE = "notification.payment.completed";

    public static final String DEAD_LETTER_QUEUE = "trip.events.dead-letter";


    @Bean
    public TopicExchange tripEventsExchange() {
        return new TopicExchange(TRIP_EVENT_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue matchedQueue() {
        return QueueBuilder.durable(MATCHED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Queue completedQueue() {
        return QueueBuilder.durable(COMPLETED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Queue cancelledQueue() {
        return QueueBuilder.durable(CANCELLED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding matchedBinding() {
        return BindingBuilder.bind(matchedQueue())
                .to(tripEventsExchange())
                .with("trip.matched");
    }

    @Bean
    public Binding completedBinding() {
        return BindingBuilder.bind(completedQueue())
                .to(tripEventsExchange())
                .with("trip.completed");
    }

    @Bean
    public Binding cancelledBinding() {
        return BindingBuilder.bind(cancelledQueue())
                .to(tripEventsExchange())
                .with("trip.cancelled");
    }

    @Bean
    public Binding paymentCompletedBinding() {
        return BindingBuilder.bind(paymentCompletedQueue())
                .to(paymentEventsExchange())
                .with("payment.completed");
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
