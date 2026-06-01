package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class DevProfileTest {

    @Autowired
    Greeter greeter;

    @Test
    void devProfileOverridesBaseGreeting() {
        assertThat(greeter.greeting()).isEqualTo("Hi from the dev environment.");
    }
}
