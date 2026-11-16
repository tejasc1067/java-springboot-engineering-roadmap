package com.example.consumer;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@SpringBootApplication
public class ConsumerDemoApplication {

    // Spring Boot's KafkaAdmin creates NewTopic beans at startup if they don't exist yet.
    @Bean
    NewTopic orders() {
        return TopicBuilder.name("orders").partitions(2).build();
    }
}
