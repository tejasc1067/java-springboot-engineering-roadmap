package com.example.rabbit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** A message whose routing key matches no binding: by default RabbitMQ drops it and tells nobody. */
class UnroutableTest extends RabbitTestBase {

    @Autowired
    ConnectionFactory connectionFactory;

    @BeforeEach
    void declare() {
        DirectExchange orders = new DirectExchange("orders.routed", false, true);
        admin.declareExchange(orders);
        Queue eu = new Queue("orders.routed.eu", false, false, true);
        admin.declareQueue(eu);
        admin.purgeQueue(eu.getName(), false);
        admin.declareBinding(BindingBuilder.bind(eu).to(orders).with("eu"));
    }

    @Test
    void noMatchingBinding_theMessageIsDropped_andThePublishStillSucceeds() {
        publish("orders.routed", "asia", "order 7");   // confirmed by the broker: no exception

        assertThat(depth("orders.routed.eu")).isZero();
        // The message is gone. No queue has it, no error was raised, nothing was logged.
    }

    @Test
    void mandatoryFlag_theBrokerReturnsTheMessageToThePublisher() {
        List<ReturnedMessage> returned = new CopyOnWriteArrayList<>();
        RabbitTemplate mandatory = new RabbitTemplate(connectionFactory);
        mandatory.setMandatory(true);
        mandatory.setReturnsCallback(returned::add);

        mandatory.convertAndSend("orders.routed", "asia", "order 7");

        await().atMost(Duration.ofSeconds(5)).until(() -> returned.size() == 1);
        assertThat(returned.get(0).getReplyText()).isEqualTo("NO_ROUTE");
        assertThat(new String(returned.get(0).getMessage().getBody())).isEqualTo("order 7");
    }

    @Test
    void alternateExchange_catchesWhatNoBindingMatched() {
        FanoutExchange unrouted = new FanoutExchange("orders.unrouted", false, true);
        admin.declareExchange(unrouted);
        Queue unroutedQueue = new Queue("orders.unrouted.q", false, false, true);
        admin.declareQueue(unroutedQueue);
        admin.purgeQueue(unroutedQueue.getName(), false);
        admin.declareBinding(BindingBuilder.bind(unroutedQueue).to(unrouted));

        admin.declareExchange(ExchangeBuilder.directExchange("orders.with-ae").autoDelete().durable(false)
                .alternate("orders.unrouted").build());
        admin.declareBinding(BindingBuilder.bind(new Queue("orders.routed.eu")).to(new DirectExchange("orders.with-ae")).with("eu"));

        publish("orders.with-ae", "asia", "order 8");

        assertThat(depth("orders.routed.eu")).isZero();
        assertThat(depth("orders.unrouted.q")).isEqualTo(1);
    }
}
