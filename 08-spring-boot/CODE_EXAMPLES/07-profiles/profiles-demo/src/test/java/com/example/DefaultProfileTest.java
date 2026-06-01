package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DefaultProfileTest {

    @Autowired
    Greeter greeter;

    @Test
    void noActiveProfileFallsBackToTheBaseFile() {
        assertThat(greeter.greeting()).isEqualTo("Hello from the default config.");
    }
}
