package com.example.amqp;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConverterTest extends RabbitTestBase {

    @Test
    void jackson2JsonMessageConverter_sendsReadableJson() {
        admin.declareQueue(new Queue("json-check", false, false, true));
        rabbit.convertAndSend("", "json-check", new OrderPlaced("o-1", 1999));

        Message message = rabbit.receive("json-check", 5_000);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"orderId\":\"o-1\",\"amountCents\":1999}");
        // The same class-name header as Spring Kafka's JsonSerializer (topic 09), with the same coupling.
        assertThat(message.getMessageProperties().getHeaders()).containsEntry("__TypeId__", "com.example.amqp.OrderPlaced");
    }
}
