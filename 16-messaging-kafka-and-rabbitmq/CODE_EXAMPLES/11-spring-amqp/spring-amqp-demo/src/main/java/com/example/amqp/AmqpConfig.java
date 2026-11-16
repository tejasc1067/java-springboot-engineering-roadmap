package com.example.amqp;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Exchanges, queues and bindings as beans: RabbitAdmin declares them on the broker when the app connects. */
@Configuration
public class AmqpConfig {

    public static final String ORDERS_EXCHANGE = "orders";
    public static final String SHIPPING_QUEUE = "shipping.orders";
    public static final String DEAD_LETTER_EXCHANGE = "orders.dlx";
    public static final String SHIPPING_DLQ = "shipping.orders.dlq";

    @Bean
    TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE);
    }

    @Bean
    Queue shippingQueue() {
        // A message this queue rejects without requeue goes to the dead-letter exchange instead of vanishing.
        return QueueBuilder.durable(SHIPPING_QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
    }

    @Bean
    Binding shippingBinding() {
        return BindingBuilder.bind(shippingQueue()).to(ordersExchange()).with("order.placed.#");
    }

    @Bean
    FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue shippingDeadLetterQueue() {
        return QueueBuilder.durable(SHIPPING_DLQ).build();
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(shippingDeadLetterQueue()).to(deadLetterExchange());
    }

    /** Boot plugs a MessageConverter bean into RabbitTemplate and every @RabbitListener. */
    @Bean
    @Profile("!java-serialization")
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
