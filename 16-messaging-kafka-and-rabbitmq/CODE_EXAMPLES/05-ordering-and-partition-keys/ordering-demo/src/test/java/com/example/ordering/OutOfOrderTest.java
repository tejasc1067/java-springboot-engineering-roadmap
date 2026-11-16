package com.example.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/** Plain Kafka clients, no Spring listener, so every step of the problem is visible. */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 3, topics = {"no-key-small", "no-key-many", "no-key-ooo"})
class OutOfOrderTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void noKey_aFewSmallEventsStillLandOnOnePartition_soItLooksFineInDev() throws Exception {
        try (Producer<String, String> producer = producer(Map.of())) {
            Set<Integer> partitions = new TreeSet<>();
            for (OrderEvent.Type type : OrderEvent.Type.values()) {
                RecordMetadata sent = producer.send(new ProducerRecord<>("no-key-small", new OrderEvent("order-1", type).value())).get();
                partitions.add(sent.partition());
            }
            // The default partitioner is "sticky" for records without a key: it fills one partition
            // until about batch.size (16 KB) has gone there, then picks another one.
            assertThat(partitions).hasSize(1);
        }
    }

    @Test
    void noKey_underRealVolume_manyOrdersHaveTheirEventsSplitAcrossPartitions() throws Exception {
        // batch.size=100 makes the sticky partitioner switch after ~100 bytes instead of 16 KB,
        // which is what a busy producer looks like.
        int ordersSplit = 0;
        try (Producer<String, String> producer = producer(Map.of(ProducerConfig.BATCH_SIZE_CONFIG, 100))) {
            for (int i = 0; i < 100; i++) {
                Set<Integer> partitionsOfThisOrder = new TreeSet<>();
                for (OrderEvent.Type type : OrderEvent.Type.values()) {
                    RecordMetadata sent = producer.send(new ProducerRecord<>("no-key-many", new OrderEvent("order-" + i, type).value())).get();
                    partitionsOfThisOrder.add(sent.partition());
                }
                if (partitionsOfThisOrder.size() > 1) {
                    ordersSplit++;
                }
            }
        }
        System.out.println("orders whose 3 events landed on more than one partition: " + ordersSplit + " of 100");
        assertThat(ordersSplit).isGreaterThan(10);
    }

    @Test
    void eventsOnDifferentPartitions_areReadInWhateverOrderTheConsumersRun() throws Exception {
        // Where the sticky partitioner might put one order's events under load: one per partition.
        try (Producer<String, String> producer = producer(Map.of())) {
            producer.send(new ProducerRecord<>("no-key-ooo", 0, null, "order-9:CREATED")).get();
            producer.send(new ProducerRecord<>("no-key-ooo", 1, null, "order-9:PAID")).get();
            producer.send(new ProducerRecord<>("no-key-ooo", 2, null, "order-9:SHIPPED")).get();
        }

        // Three consumers in one group, one partition each. Nothing coordinates them, so any of them
        // may run first. Here the one for partition 0 is slowest (a GC pause, a slow database call).
        OrderStatusView view = new OrderStatusView();
        for (int partition : new int[] {2, 1, 0}) {
            try (Consumer<String, String> consumer = consumerOf(new TopicPartition("no-key-ooo", partition))) {
                for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10), 1)) {
                    view.apply(OrderEvent.parse(record.value()));
                }
            }
        }

        assertThat(view.receivedFor("order-9")).containsExactly(
                OrderEvent.Type.SHIPPED, OrderEvent.Type.PAID, OrderEvent.Type.CREATED);
        assertThat(view.statusOf("order-9")).isEqualTo(OrderEvent.Type.CREATED);   // it shipped, the view says "created"
    }

    private Producer<String, String> producer(Map<String, Object> overrides) {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.putAll(overrides);
        return new KafkaProducer<>(props);
    }

    private Consumer<String, String> consumerOf(TopicPartition partition) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("ooo-" + partition.partition(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        consumer.assign(Set.of(partition));
        return consumer;
    }
}
