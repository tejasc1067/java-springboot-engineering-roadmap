package com.example.amqp;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** convertAndSend returns before the broker has the message. A confirm tells you whether it does. */
class PublisherConfirmsTest extends RabbitTestBase {

    @Test
    void routedMessage_isAcked() throws Exception {
        CorrelationData correlation = new CorrelationData("order-1");
        rabbit.convertAndSend(AmqpConfig.ORDERS_EXCHANGE, "order.placed.eu", new OrderPlaced("o-1", 1999), correlation);

        assertThat(correlation.getFuture().get(5, TimeUnit.SECONDS).isAck()).isTrue();
        assertThat(correlation.getReturned()).isNull();
    }

    @Test
    void unroutableMessage_isStillAcked_butComesBackAsReturned() throws Exception {
        CorrelationData correlation = new CorrelationData("invoice-1");
        rabbit.convertAndSend(AmqpConfig.ORDERS_EXCHANGE, "invoice.created", new OrderPlaced("o-1", 1999), correlation);

        // ack only means "the broker has taken it". Routed to no queue, it's acked and dropped,
        // so check the return too.
        assertThat(correlation.getFuture().get(5, TimeUnit.SECONDS).isAck()).isTrue();
        assertThat(correlation.getReturned()).isNotNull();
        assertThat(correlation.getReturned().getReplyText()).isEqualTo("NO_ROUTE");
    }

    @Test
    void missingExchange_isNacked() throws Exception {
        CorrelationData correlation = new CorrelationData("order-2");
        rabbit.convertAndSend("ordres", "order.placed.eu", new OrderPlaced("o-2", 1999), correlation);   // typo in the exchange name

        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        assertThat(confirm.isAck()).isFalse();
        assertThat(confirm.getReason()).contains("NOT_FOUND");
    }
}
