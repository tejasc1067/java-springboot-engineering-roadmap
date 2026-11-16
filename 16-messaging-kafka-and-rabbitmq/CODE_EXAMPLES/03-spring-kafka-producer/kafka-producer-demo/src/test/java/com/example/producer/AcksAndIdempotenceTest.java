package com.example.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

/** What the producer actually runs with. No broker needed: these are the client's own config rules. */
class AcksAndIdempotenceTest {

    @Test
    void theDefaults_areAcksAll_andIdempotenceOn() {
        ProducerConfig defaults = new ProducerConfig(baseConfig());

        assertThat(defaults.getString(ProducerConfig.ACKS_CONFIG)).isEqualTo("-1");   // -1 means "all"
        assertThat(defaults.getBoolean(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isTrue();
    }

    @Test
    void settingAcks1_silentlyTurnsIdempotenceOff() {
        Map<String, Object> config = baseConfig();
        config.put(ProducerConfig.ACKS_CONFIG, "1");

        assertThat(new ProducerConfig(config).getBoolean(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isFalse();
    }

    @Test
    void askingForIdempotenceWithAcks1_isRejected() {
        Map<String, Object> config = baseConfig();
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        assertThatThrownBy(() -> new ProducerConfig(config))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("acks");
    }

    private static Map<String, Object> baseConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return config;
    }
}
