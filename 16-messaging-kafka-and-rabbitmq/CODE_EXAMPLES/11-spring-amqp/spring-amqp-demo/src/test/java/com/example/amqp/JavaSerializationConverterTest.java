package com.example.amqp;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.test.context.ActiveProfiles;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Without a MessageConverter bean, RabbitTemplate uses SimpleMessageConverter: Java serialization. */
@ActiveProfiles("java-serialization")
class JavaSerializationConverterTest extends RabbitTestBase {

    record SerializableOrder(String orderId, long amountCents) implements Serializable { }

    @Test
    void aPlainRecord_cannotBeSentAtAll() {
        assertThatThrownBy(() -> rabbit.convertAndSend("", "no-matter", new OrderPlaced("o-1", 1999)))
                .hasMessageContaining("SimpleMessageConverter only supports String, byte[] and Serializable payloads");
    }

    @Test
    void aSerializableRecord_isSent_butTheReceiverRefusesToDeserializeIt() {
        admin.declareQueue(new Queue("java-serialized", false, false, true));
        rabbit.convertAndSend("", "java-serialized", new SerializableOrder("o-1", 1999));

        // Spring AMQP only deserializes classes on an allow-list: deserializing untrusted Java bytes is an attack vector.
        assertThatThrownBy(() -> rabbit.receiveAndConvert("java-serialized", 5_000))
                .hasStackTraceContaining("Attempt to deserialize unauthorized class");
    }
}
