package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ValidRetryPropertiesTest {

    @Autowired
    RetryProperties properties;

    @Autowired
    RetryService service;

    @Test
    void propertiesBindFromApplicationYml() {
        assertThat(properties.maxAttempts()).isEqualTo(3);
        assertThat(properties.backoff()).isEqualTo(Duration.ofMillis(500));
        assertThat(properties.notification().email()).isEqualTo("ops@example.com");
        assertThat(properties.notification().enabled()).isTrue();
    }

    @Test
    void retryServiceIsWiredWithTheBoundProperties() {
        assertThat(service.config()).isSameAs(properties);
        assertThat(service.describePolicy())
                .isEqualTo("max=3 backoff=500ms notify=ops@example.com enabled=true");
    }
}
