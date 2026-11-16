package com.example.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

class JsonSerializationTest extends KafkaTestBase {

    @Autowired
    OrderPublisher publisher;

    @Autowired
    EmbeddedKafkaBroker kafka;

    @Test
    void theRecordIsJsonBytes_plusATypeHeader() throws Exception {
        RecordMetadata sent = publisher.publish(new OrderPlaced("o-json", "customer-json", 4250)).getRecordMetadata();

        // Read it back as plain text: Kafka stores only bytes and has no idea what an OrderPlaced is.
        try (KafkaConsumer<String, String> raw = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            TopicPartition partition = new TopicPartition(sent.topic(), sent.partition());
            raw.assign(List.of(partition));
            raw.seek(partition, sent.offset());
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getRecords(raw, Duration.ofSeconds(10)).records(partition).get(0);

            assertThat(record.key()).isEqualTo("customer-json");
            assertThat(record.value())
                    .isEqualTo("{\"orderId\":\"o-json\",\"customerId\":\"customer-json\",\"totalCents\":4250}");
            // JsonSerializer also records the Java class name in a header, for Spring consumers to use.
            assertThat(new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
                    .isEqualTo("com.example.producer.OrderPlaced");
        }
    }
}
