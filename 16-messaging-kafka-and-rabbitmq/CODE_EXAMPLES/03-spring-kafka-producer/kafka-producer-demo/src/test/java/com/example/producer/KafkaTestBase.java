package com.example.producer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
// The broker's record limit is lowered from its 1 MB default so the "too large" test can use a small record.
@EmbeddedKafka(kraft = true, partitions = 3, topics = OrderPublisher.TOPIC,
        brokerProperties = "message.max.bytes=10000")
abstract class KafkaTestBase {
}
