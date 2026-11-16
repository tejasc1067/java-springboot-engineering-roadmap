package com.example.rabbit;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

/** A producer never publishes to a queue. It publishes to an exchange, and bindings decide which queues get a copy. */
class ExchangeTypesTest extends RabbitTestBase {

    private Queue queue(String name) {
        Queue queue = new Queue(name, false, false, true);   // not durable, auto-delete: test-only queues
        admin.declareQueue(queue);
        admin.purgeQueue(name, false);
        return queue;
    }

    @Test
    void defaultExchange_routesToTheQueueNamedByTheRoutingKey() {
        queue("emails");

        publish("", "emails", "welcome ann");   // "" is the default exchange

        assertThat(rabbit.receiveAndConvert("emails")).isEqualTo("welcome ann");
    }

    @Test
    void directExchange_routingKeyMustMatchTheBindingKeyExactly() {
        DirectExchange orders = new DirectExchange("orders.direct", false, true);
        admin.declareExchange(orders);
        admin.declareBinding(BindingBuilder.bind(queue("orders.eu")).to(orders).with("eu"));
        admin.declareBinding(BindingBuilder.bind(queue("orders.us")).to(orders).with("us"));

        publish("orders.direct", "eu", "order 1");

        assertThat(depth("orders.eu")).isEqualTo(1);
        assertThat(depth("orders.us")).isZero();
    }

    @Test
    void topicExchange_starMatchesOneWord_hashMatchesZeroOrMore() {
        TopicExchange events = new TopicExchange("events.topic", false, true);
        admin.declareExchange(events);
        admin.declareBinding(BindingBuilder.bind(queue("order-events-one-level")).to(events).with("order.*"));
        admin.declareBinding(BindingBuilder.bind(queue("all-order-events")).to(events).with("order.#"));
        admin.declareBinding(BindingBuilder.bind(queue("eu-anything")).to(events).with("*.*.eu"));

        publish("events.topic", "order.created", "1");
        publish("events.topic", "order.created.eu", "2");
        publish("events.topic", "order", "3");
        publish("events.topic", "payment.failed.eu", "4");

        assertThat(depth("order-events-one-level")).isEqualTo(1);   // order.created only
        assertThat(depth("all-order-events")).isEqualTo(3);         // order.created, order.created.eu, and order itself
        assertThat(depth("eu-anything")).isEqualTo(2);              // order.created.eu, payment.failed.eu
    }

    @Test
    void fanoutExchange_everyBoundQueueGetsACopy_routingKeyIgnored() {
        FanoutExchange placed = new FanoutExchange("order-placed.fanout", false, true);
        admin.declareExchange(placed);
        admin.declareBinding(BindingBuilder.bind(queue("shipping.order-placed")).to(placed));
        admin.declareBinding(BindingBuilder.bind(queue("billing.order-placed")).to(placed));

        publish("order-placed.fanout", "whatever", "order 1");

        assertThat(depth("shipping.order-placed")).isEqualTo(1);
        assertThat(depth("billing.order-placed")).isEqualTo(1);
    }

    @Test
    void springDefaults_durableQueue_andPersistentMessages() {
        // The raw RabbitMQ client defaults to neither. Spring AMQP defaults to both.
        assertThat(new Queue("orders").isDurable()).isTrue();

        queue("persistent-check");
        publish("", "persistent-check", "x");
        assertThat(rabbit.receive("persistent-check").getMessageProperties().getReceivedDeliveryMode())
                .hasToString("PERSISTENT");
    }
}
