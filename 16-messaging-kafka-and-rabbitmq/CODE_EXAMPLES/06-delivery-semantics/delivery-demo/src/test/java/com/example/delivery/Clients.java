package com.example.delivery;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/** Plain Kafka clients for the tests, so every commit is visible in the test code. */
final class Clients {

    private Clients() {
    }

    static Producer<String, String> producer(EmbeddedKafkaBroker broker, Map<String, Object> overrides) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.producerProps(broker));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.putAll(overrides);
        return new KafkaProducer<>(props);
    }

    /** Auto-commit off: the consumer commits only when the test calls commitSync(). */
    static Consumer<String, String> consumer(EmbeddedKafkaBroker broker, String group, Map<String, Object> overrides) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps(group, "false", broker));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putAll(overrides);
        return new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
    }
}
