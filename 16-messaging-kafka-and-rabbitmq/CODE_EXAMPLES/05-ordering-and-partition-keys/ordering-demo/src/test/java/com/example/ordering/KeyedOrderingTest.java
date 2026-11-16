package com.example.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = "order-events")
class KeyedOrderingTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    OrderEventsListener listener;

    @Test
    void keyedByOrderId_everyOrdersEventsArriveInOrder_evenWithThreeConcurrentConsumers() {
        for (int i = 0; i < 50; i++) {
            for (OrderEvent.Type type : OrderEvent.Type.values()) {
                String orderId = "order-" + i;
                kafka.send("order-events", orderId, new OrderEvent(orderId, type).value());   // key = orderId
            }
        }

        OrderStatusView view = listener.view();
        await().atMost(Duration.ofSeconds(20)).until(() -> view.orderCount() == 50
                && view.receivedFor("order-49").size() == 3);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            for (int i = 0; i < 50; i++) {
                assertThat(view.receivedFor("order-" + i)).containsExactly(
                        OrderEvent.Type.CREATED, OrderEvent.Type.PAID, OrderEvent.Type.SHIPPED);
                assertThat(view.statusOf("order-" + i)).isEqualTo(OrderEvent.Type.SHIPPED);
            }
        });
    }
}
