package com.example.consumer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(kraft = true, partitions = 2, topics = {"payments", "invoices", "refunds", "shipments", "receipts"})
abstract class KafkaTestBase {
}
